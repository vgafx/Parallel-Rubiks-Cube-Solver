package rubiks.ipl;
/**
 *  Class containing variable declarations that are common for both master / worker
 *  It creates ibis, performs a master election, and starts the node as a master or worker
 *
 *  @author Vasileios Gkanasoulis VU ID:2617203
 */



import ibis.ipl.*;
import java.util.LinkedList;

public class Node{

    public static Ibis ibis;
    public static Registry reg;

    public static int numNodes = 0;
    public static int rank = -1;

    public static long totalTime = 0;
    public static long startTime = 0;

    public static int m_twists, m_seed, m_size;

    public static Cube initCube;
    public static int solutions=0;
    public static int bound = 0;


    public IbisCapabilities ibisCapabilities = new IbisCapabilities
    (IbisCapabilities.ELECTIONS_STRICT,
    IbisCapabilities.CLOSED_WORLD,  
    IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED);

    /*Port used by the master node to send notifications to workers*/
    public static PortType broadcastPort = new PortType 
    (PortType.COMMUNICATION_RELIABLE,
    PortType.COMMUNICATION_FIFO,    
    PortType.SERIALIZATION_DATA,
    PortType.RECEIVE_EXPLICIT,
    PortType.CONNECTION_ONE_TO_MANY);

    /*Port for distributing work*/
    public static PortType workPort = new PortType 
    (PortType.COMMUNICATION_RELIABLE,
    PortType.COMMUNICATION_FIFO,
    PortType.SERIALIZATION_OBJECT,
    PortType.SERIALIZATION_OBJECT_IBIS,
    PortType.RECEIVE_EXPLICIT,
    PortType.CONNECTION_ONE_TO_MANY);

    /*Port used by worker nodes to send solutions back to the master*/
    public static PortType solutionPort = new PortType
    (PortType.COMMUNICATION_RELIABLE,
    PortType.SERIALIZATION_OBJECT,
    PortType.RECEIVE_EXPLICIT,
    PortType.CONNECTION_MANY_TO_ONE);

    /*Port for sending info to a single worker*/
    public static PortType singleInfoPort = new PortType 
    (PortType.COMMUNICATION_RELIABLE,
    PortType.COMMUNICATION_FIFO,
    PortType.SERIALIZATION_OBJECT,
    PortType.SERIALIZATION_OBJECT_IBIS,
    PortType.RECEIVE_EXPLICIT,
    PortType.CONNECTION_ONE_TO_ONE);



    /*Constructor*/
    public Node (Cube cube, int size, int twists, int seed) throws Exception{
        /*Creat Ibis with specified port types*/
        ibis = IbisFactory.createIbis(ibisCapabilities, null, broadcastPort, workPort, solutionPort, singleInfoPort);
        initCube = cube;
        m_twists = twists;
        m_size = initCube.getSize();
        m_seed = seed;
    }


    public void run() throws Exception{
        reg = ibis.registry();
        /*Elect a node to become the master*/
        IbisIdentifier master = ibis.registry().elect("Master");
        //System.out.println("Master is " + master);

        if (master.equals(ibis.identifier())){
            System.out.println("Searching for solution for cube of size "
                + m_size + ", twists = " + m_twists + ", seed = " + m_seed);
            initCube.print(System.out);
            System.out.flush();
            totalTime = new Master(master, 2).run();
        } else {
            new Worker(master, 2).run();
        }

        if (master.equals(ibis.identifier())){
            System.err.println("Solving cube took " + totalTime + " milliseconds");
        }

        ibis.end();
    }


}