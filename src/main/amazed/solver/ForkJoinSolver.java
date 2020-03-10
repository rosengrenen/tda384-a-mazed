package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinTask;

/**
 * <code>ForkJoinSolver</code> implements a solver for <code>Maze</code> objects
 * using a fork/join multi-thread depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */

public class ForkJoinSolver extends SequentialSolver {
    private int current;
    private CopyOnWriteArrayList<ForkJoinTask<List<Integer>>> threads = new CopyOnWriteArrayList<>();
    int player;

    /**
     * Creates a solver that searches in <code>maze</code> from the start node to a
     * goal.
     *
     * @param maze the maze to be searched
     */
    public ForkJoinSolver(Maze maze) {
        super(maze);
        current = start;
        player = maze.newPlayer(start);
        visited = new ConcurrentSkipListSet<>();
        predecessor = new ConcurrentSkipListMap<>();
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the start node to a
     * goal, forking after a given number of visited nodes.
     *
     * @param maze      the maze to be searched
     * @param forkAfter the number of steps (visited nodes) after which a parallel
     *                  task is forked; if <code>forkAfter &lt;= 0</code> the solver
     *                  never forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter) {
        super(maze);
        this.forkAfter = forkAfter;
        current = start;
        player = maze.newPlayer(start);
        visited = new ConcurrentSkipListSet<>();
        predecessor = new ConcurrentSkipListMap<>();
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the start node to a
     * goal, forking after a given number of visited nodes.
     *
     * @param maze      the maze to be searched
     * @param forkAfter the number of steps (visited nodes) after which a parallel
     *                  task is forked; if <code>forkAfter &lt;= 0</code> the solver
     *                  never forks new tasks
     * @param current   current location
     */
    public ForkJoinSolver(Maze maze, int forkAfter, Set<Integer> visited, Map<Integer, Integer> predecessor,
            int current) {
        super(maze);
        this.forkAfter = forkAfter;
        this.current = current;
        player = maze.newPlayer(current);
        this.visited = visited;
        this.predecessor = predecessor;
    }

    /**
     * Searches for and returns the path, as a list of node identifiers, that goes
     * from the start node to a goal node in the maze. If such a path cannot be
     * found (because there are no goals, or all goals are unreacheable), the method
     * returns <code>null</code>.
     *
     * @return the list of node identifiers from the start node to a goal node in
     *         the maze; <code>null</code> if such a path cannot be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch(current);
    }

    private List<Integer> parallelSearch(int current) {
        visited.add(current);

        if (maze.hasGoal(current)) {
            return pathFromTo(start, current);
        }

        Set<Integer> neighbours = maze.neighbors(current);
        Set<Integer> unvisitedNeighbours = new HashSet<>();
        for (Integer next : neighbours) {
            if (!visited.contains(next)) {
                unvisitedNeighbours.add(next);
                predecessor.put(next, current);
            }
        }

        if (unvisitedNeighbours.size() > 1) {
            for (Integer next : unvisitedNeighbours) {
                threads.add(new ForkJoinSolver(maze, forkAfter, visited, predecessor, next).fork());
            }
        } else if (unvisitedNeighbours.size() == 1) {
            Integer next = unvisitedNeighbours.iterator().next();
            maze.move(player, next);
            return parallelSearch(next);
        } else {
            return null;
        }

        List<Integer> result = null;
        for (ForkJoinTask<List<Integer>> thread : threads) {
            List<Integer> threadResult = thread.join();
            if (threadResult != null) {
                result = threadResult;
            }
        }

        return result;
    }
}
