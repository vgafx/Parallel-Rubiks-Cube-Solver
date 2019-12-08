package rubiks.bonus;
/**
 *  Class containing all the code to be run by a master type node. The worker receives
 *  essential information from the masted (ID and numNodes). It then computes which
 *  generated children cubes correspond to this worker. 
 *  The master and the workers proceed through depths (bound) together.
 *  The workers check with the master if a new round of cumputation is to be done
 *  or if they should terminate.
 *
 *  @author Vasileios Gkanasoulis VU ID:2617203
 */
import ibis.ipl.*;
import java.util.LinkedList;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;


public class Worker {
    //LinkedList<Cube> workerQueue;
    LinkedList<Cube> tempQueue;
    LinkedList<Cube> workQueue;
    LinkedBlockingQueue<Cube> threadQueue;
    IbisIdentifier master;
    static int bound;
    int terminationSignal = 0;
    int wStart = 0, wEnd = 0;
    int remainingCubes, cubesPerNode;
    int solvedAtMaster;
    int c_i = 0;
    boolean unevenDist=false;

    private static workerThread[] threads;

    /*Ports for worker*/
    ReceivePort recvCubePort; //workPort
    ReceivePort recvInfoPort; //broadcastPort
    ReceivePort recvSingleInfoPort; //singleInfoPort
    SendPort    sendSolutionPort; //solutionPort




    public Worker(IbisIdentifier mServer, int slvMaster) throws IOException {
        master=mServer;
        solvedAtMaster = slvMaster;
        /*Init ports and enable connections*/
        recvCubePort = Node.ibis.createReceivePort(Node.workPort,"cube");
        recvCubePort.enableConnections();
        recvInfoPort = Node.ibis.createReceivePort(Node.broadcastPort,"broadcast");
        recvInfoPort.enableConnections();
        recvSingleInfoPort = Node.ibis.createReceivePort(Node.singleInfoPort,"singleinfo");
        recvSingleInfoPort.enableConnections();
        sendSolutionPort = Node.ibis.createSendPort(Node.solutionPort);
        sendSolutionPort.connect(master, "solution");  

    }

    public void run() throws IOException{
        tempQueue = new LinkedList<Cube>();
        workQueue = new LinkedList<Cube>();
        threadQueue = new LinkedBlockingQueue<Cube>();
        threads = new workerThread[Node.numThreads];

        /*Get my ID from master*/
        ReadMessage id_msg = recvSingleInfoPort.receive();
        Node.rank = id_msg.readInt();
        id_msg.finish();
        recvSingleInfoPort.close();

        /*Get number of nodes in the pool by master*/
        ReadMessage node_msg = recvInfoPort.receive();
        Node.numNodes = node_msg.readInt();
        node_msg.finish();

        try { //Get work list from master
            ReadMessage q_msg = recvCubePort.receive();
            tempQueue.addAll((LinkedList<Cube>) q_msg.readObject());
            q_msg.finish();
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*Figure out which cubes correspond to this worker*/
        remainingCubes = tempQueue.size() % Node.numNodes;
        cubesPerNode = (int) tempQueue.size() / Node.numNodes;
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
        Cube tempCube = tempQueue.peek();
        /*Get cubes that correspond to this worker*/
        for (int w = wStart; w <= wEnd; w++){
            workQueue.add(tempQueue.get(w));
        }

        
        Node.bound = tempCube.getBound();


        /*Start computing*/
        while(true){
            threadQueue.addAll(workQueue);
            Node.bound++;

            for (int t = 0; t < Node.numThreads; t++) {
                threads[t] = new workerThread();
                threads[t].start();
            }

            for (int t = 0; t < Node.numThreads; t++){
                try {
                    threads[t].join();
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }

            /*Inform master of any solutions found*/
            WriteMessage sol_msg = sendSolutionPort.newMessage();
            sol_msg.writeInt(Node.solutions);
            sol_msg.finish();

            /*Receive term / cont signal from Master*/
            ReadMessage signal_msg = recvInfoPort.receive();
            terminationSignal = signal_msg.readInt();
            signal_msg.finish();

            if (terminationSignal == 1) break;
        }

        sendSolutionPort.close();
        recvInfoPort.close();

    }


/*Thread routine*/
public class workerThread extends Thread {

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
                res = Rubiks.nodeSolve(currCube , Node.bound, solvedAtMaster);
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