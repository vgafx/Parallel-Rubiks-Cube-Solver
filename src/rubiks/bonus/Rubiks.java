package rubiks.bonus;
import java.util.LinkedList;

/**
 * Solver for rubik's cube puzzle.
 *
 * @author Niels Drost, Timo van Kessel
 *
 */
public class Rubiks {

    public static final boolean PRINT_SOLUTION = false;
    public static int workItems = 0;

    public static int getWorkItems(){
        return workItems;
    }

    /*Function that generates children up to specified depth
     *Fixed depth =2
     *To be used by master for generating initial jobs for workers
     */
    public static LinkedList<Cube> solveAtMaster(Cube cube, CubeCache cache, int depth){
        int generatedChildren = 0;
        System.out.print("Bound now:");
        if (cube.isSolved()){
            System.out.println();
            System.out.println("Solving cube possible in 1 ways of 0 steps");
        }
        if (cube.getTwists() >= cube.getBound()) {
        }
        LinkedList<Cube> tempQueue = new LinkedList<Cube>();
        Cube[] children = cube.generateChildren(cache);
        System.out.print(" " + 1);
        for (Cube child : children){
            if (cube.isSolved()){
                System.out.println();
                System.out.println("Solving cube possible in 1 ways of 1 steps");
            }

            Cube[] grandChildren = child.generateChildren(cache);

            for (Cube grandChild : grandChildren){
                if (cube.isSolved()){
                    System.out.println();
                    System.out.println("Solving cube possible in 1 ways of 2 steps");
                }
                grandChild.setBound(depth);
                tempQueue.add(grandChild);
                generatedChildren++;
            }

        }
        System.out.print(" " + depth);
        workItems = generatedChildren;
        return tempQueue;
    }


    public static int nodeSolve(Cube cube, int currentBound, int solvedAtMaster) {
        CubeCache cache = new CubeCache(cube.getSize());
        int bound = 0;
        int result = 0;


        while (result == 0 && (bound + solvedAtMaster)  < currentBound) {
            bound++;
            cube.setBound(solvedAtMaster + bound);

            result += solutions(cube, cache);
        }

        return result;
    }
    /**
     * Recursive function to find a solution for a given cube. Only searches to
     * the bound set in the cube object.
     *
     * @param cube
     *            cube to solve
     * @param cache
     *            cache of cubes used for new cube objects
     * @return the number of solutions found
     */
    private static int solutions(Cube cube, CubeCache cache) {
        if (cube.isSolved()) {
            return 1;
        }

        if (cube.getTwists() >= cube.getBound()) {
            return 0;
        }

        // generate all possible cubes from this one by twisting it in
        // every possible way. Gets new objects from the cache
        Cube[] children = cube.generateChildren(cache);

        int result = 0;

        for (Cube child : children) {
            // recursion step
            int childSolutions = solutions(child, cache);
            if (childSolutions > 0) {
                result += childSolutions;
                if (PRINT_SOLUTION) {
                    child.print(System.err);
                }
            }
            // put child object in cache
            cache.put(child);
        }

        return result;
    }

    /**
     * Solves a Rubik's cube by iteratively searching for solutions with a
     * greater depth. This guarantees the optimal solution is found. Repeats all
     * work for the previous iteration each iteration though...
     *
     * @param cube
     *            the cube to solve
     */
    public static void solve(Cube cube) {
        // cache used for cube objects. Doing new Cube() for every move
        // overloads the garbage collector
        CubeCache cache = new CubeCache(cube.getSize());
        int bound = 0;
        int result = 0;

        System.out.print("Bound now:");

        while (result == 0) {
            bound++;
            cube.setBound(bound);

            System.out.print(" " + bound);
            result = solutions(cube, cache);
        }

        System.out.println();
        System.out.println("Solving cube possible in " + result + " ways of "
                + bound + " steps");
    }

    public static void printUsage() {
        System.out.println("Rubiks Cube solver");
        System.out.println("");
        System.out
                .println("Does a number of random twists, then solves the rubiks cube with a simple");
        System.out
                .println(" brute-force approach. Can also take a file as input");
        System.out.println("");
        System.out.println("USAGE: Rubiks [OPTIONS]");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("--size SIZE\t\tSize of cube (default: 3)");
        System.out
                .println("--twists TWISTS\t\tNumber of random twists (default: 11)");
        System.out
                .println("--seed SEED\t\tSeed of random generator (default: 0");
        System.out
                .println("--threads THREADS\t\tNumber of threads to use (default: 1, other values not supported by sequential version)");
        System.out.println("");
        System.out
                .println("--file FILE_NAME\t\tLoad cube from given file instead of generating it");
        System.out.println("");
    }

    /**
     * Main function.
     *
     * @param arguments
     *            list of arguments
     */
    public static void main(String[] arguments) {
        Cube cube = null;

        // default parameters of puzzle
        int size = 3;
        int twists = 11;
        int seed = 0;
        int threadNum = 8;
        String fileName = null;

        // number of threads used to solve puzzle
        // (not used in sequential version)

        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i].equalsIgnoreCase("--size")) {
                i++;
                size = Integer.parseInt(arguments[i]);
            } else if (arguments[i].equalsIgnoreCase("--twists")) {
                i++;
                twists = Integer.parseInt(arguments[i]);
            } else if (arguments[i].equalsIgnoreCase("--seed")) {
                i++;
                seed = Integer.parseInt(arguments[i]);
            } else if (arguments[i].equalsIgnoreCase("--file")) {
                i++;
                fileName = arguments[i];
            } else if (arguments[i].equalsIgnoreCase("--threads")){
                i++;
                threadNum = Integer.parseInt(arguments[i]);
            } else if (arguments[i].equalsIgnoreCase("--help") || arguments[i].equalsIgnoreCase("-h")) {
                printUsage();
                System.exit(0);
            } else {
                System.err.println("unknown option : " + arguments[i]);
                printUsage();
                System.exit(1);
            }
        }

        // create cube
        if (fileName == null) {
            cube = new Cube(size, twists, seed);
        } else {
            try {
                cube = new Cube(fileName);
            } catch (Exception e) {
                System.err.println("Cannot load cube from file: " + e);
                System.exit(1);
            }
        }


        try{
            Node node = new Node (cube, size, twists, seed, threadNum);
            node.run();
        } catch (Exception e){
            e.printStackTrace();
        }

    }

}
