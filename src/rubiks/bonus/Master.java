package rubiks.bonus;
/**
 *  Class containing all the code to be run by a master type node. The master generates
 *  children cubes from the initial cube. Then it informs workers of their assigned ids
 *  and the total number of participating nodes.
 *  It then distributes the generated cubes to the workers and computes which of the
 *  generated children cubes will be solved locally.
 *  The master and the workers proceed through depths (bound) together.
 *  At each new depth, the master check is solutions are found and informes the workers
 *  of whether if to proceed at a new depth or terminate.
 *
 *  @author Vasileios Gkanasoulis VU ID:2617203
 */
import ibis.ipl.*;
import java.util.LinkedList;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;


public class Master {
    long elapsedTime = 0;
    long startTime = 0;
    long endTime = 0;
    long masterStartTime = 0, masterEndTime = 0, masterElapsedTime = 0;
    static int s_bound;
    int workerID = 0;
    int workCubes = 0;
    int wStart = 0, wEnd = 0;
    int c_i = 0;
    int remainingCubes, cubesPerNode;
    int slvMaster;
    IbisIdentifier myself;
    CubeCache initCache;
    //signals for termination / continuation
    static final int TERMINATE = 1;
    static final int CONTINUE = 0;
    IbisIdentifier[] ibisesInPool;

    /*Cubes meant for workers are place here*/
    LinkedList<Cube> workQueue;
    /*Cubes that the master will solve go here*/
    LinkedList<Cube> localQueue;

    LinkedBlockingQueue<Cube> threadQueue;
    private static masterThread[] threads;

    /*Ports for master*/
    SendPort    sendInfoPort; //Broadcast port
    SendPort    sendSingleInfoPort; //singleInfoPort
    SendPort    sendCubePort; //workPort
    ReceivePort recvSolutionPort; //solutionPort

    


    public Master(IbisIdentifier mServer, int masterDepth) throws IOException{
        myself = mServer;
        slvMaster = masterDepth;
        /*Init variables/datastructures*/
        workQueue = new LinkedList<Cube>();
        localQueue = new LinkedList<Cube>();
        threadQueue = new LinkedBlockingQueue<Cube>();
        /*Init ports and enable connections*/
        sendInfoPort = Node.ibis.createSendPort(Node.broadcastPort);
        sendCubePort = Node.ibis.createSendPort(Node.workPort);
        recvSolutionPort = Node.ibis.createReceivePort(Node.solutionPort,"solution");
        recvSolutionPort.enableConnections();

    }



    public long run() throws IOException{
        Node.reg.waitUntilPoolClosed(); //Wait for all nodes to join
        ibisesInPool = Node.reg.joinedIbises();
        Node.numNodes = ibisesInPool.length; // get the number of nodes
        initCache = new CubeCache(Node.initCube.getSize());
        threads = new masterThread[Node.numThreads];


        /*Connect to all workers*/
        for (int n = 0; n < Node.numNodes; n++){
            if (!ibisesInPool[n].equals(myself)){
                sendInfoPort.connect(ibisesInPool[n], "broadcast");
                sendCubePort.connect(ibisesInPool[n], "cube");
            }
        }

        /*Inform workers of their assigned ID*/
        for (int n = 0; n < Node.numNodes; n++){
            if (!ibisesInPool[n].equals(myself)){
                sendSingleInfoPort = Node.ibis.createSendPort(Node.singleInfoPort);
                sendSingleInfoPort.connect(ibisesInPool[n], "singleinfo");
                WriteMessage id_msg = sendSingleInfoPort.newMessage();
                id_msg.writeInt(workerID);
                id_msg.finish();
                workerID++;
                sendSingleInfoPort.disconnect(ibisesInPool[n], "singleinfo");
                sendSingleInfoPort.close();
            } else {
                workerID++;
                Node.rank = n;
            }
        }

        /*Start timing here*/
        startTime = System.currentTimeMillis();
        /*Generate Cubes from initial cube at Master. Depth=2*/
        workQueue = Rubiks.solveAtMaster(Node.initCube, initCache, slvMaster);
        workCubes = Rubiks.getWorkItems();


        /*Inform workers of the groups size*/
        WriteMessage nodes_msg = sendInfoPort.newMessage();
        nodes_msg.writeInt(Node.numNodes);
        nodes_msg.finish();

        /*Send the workQueue to the workers*/
        WriteMessage q_msg = sendCubePort.newMessage();
        q_msg.writeObject(workQueue);
        q_msg.finish();

        /*Figure out which cubes will be assigned to which nodes*/
        remainingCubes = workQueue.size() % Node.numNodes;
        cubesPerNode = (int) workQueue.size() / Node.numNodes;

        for (int i = 0; i < Node.numNodes; i++){
            if(i <= remainingCubes) c_i = i;
            if (i == Node.rank){
                if (remainingCubes == 0){
                    wStart = i * cubesPerNode;
                    wEnd = wStart + cubesPerNode - 1;
                } else {
                    wStart = (i * cubesPerNode) + c_i;
                    wEnd = (i < remainingCubes) ? wStart + cubesPerNode : wStart + cubesPerNode -1;
                }

            }
        }

        for (int w = wStart; w <= wEnd; w++){
            localQueue.add(workQueue.get(w));
        }

        Node.bound = slvMaster;

        

        while(true){

            threadQueue.addAll(localQueue);
            Node.bound++;
            System.out.print(" " + Node.bound);

            for (int t = 0; t < Node.numThreads; t++){
                threads[t] = new masterThread();
                threads[t].start();
            }

            for (int t = 0; t < Node.numThreads; t++){
                try {
                    threads[t].join();
                } catch (InterruptedException e){
                    e.printStackTrace();    
                }
            }


            /*Check if any of the workers found a solution*/
            for (int n = 0; n < Node.numNodes; n++){
                if (!ibisesInPool[n].equals(myself)){
                    ReadMessage sol_msg = recvSolutionPort.receive();
                    int workerSolution = sol_msg.readInt();
                    sol_msg.finish();
                    Node.solutions += workerSolution;
                }
            }

            /*If solutions are found, inform workers to stop*/
            if(Node.solutions !=0){
                WriteMessage term_msg = sendInfoPort.newMessage();
                term_msg.writeInt(TERMINATE);
                term_msg.finish();
                break; 
            } else {
                WriteMessage cont_msg = sendInfoPort.newMessage();
                cont_msg.writeInt(CONTINUE);
                cont_msg.finish();
            }

        }

        /*Computations done. Record time, close ports and return*/
        endTime = System.currentTimeMillis();
        elapsedTime = endTime - startTime;
        System.out.println();
        System.out.println("Solving cube possible in " + Node.solutions + " ways of "
                + Node.bound + " steps");
        sendInfoPort.close();
        recvSolutionPort.close();
        return elapsedTime;
    }



/*Thread routine*/
public class masterThread extends Thread {

    public void run(){
        while(true){
            int res = 0;
            Cube currCube = null;

            if (threadQueue.isEmpty()){
                return;
            }

            currCube = threadQueue.poll();

            if (currCube == null){
                break;
            } else {
                res = Rubiks.nodeSolve(currCube , Node.bound, slvMaster);
                incSolutions(res);
            }

        }
    }

}

/*Protect against concurrent wirtes*/
private synchronized void incSolutions (int sol){
    Node.solutions +=sol;
}


}