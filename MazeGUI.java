import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class MazeGUI extends JFrame {

    private final int CELL_SIZE = 20;
    private final int ROWS = 30, COLS = 30;
    private final int WIDTH = COLS * CELL_SIZE, HEIGHT = ROWS * CELL_SIZE;

    Cell[][] grid;
    Cell start, end;

    boolean mazeGenerated = false;

    JButton generateButton, solveBFSButton, solveDFSButton, solveAStarButton, resetButton;
    JSlider speedSlider;
    int speed = 5;
    MazePanel mazePanel;
    boolean selectingStart = true;
    boolean selectingEnd = false;
    private javax.swing.Timer timer;
    private long startTime;
    private JLabel timerLabel;

    public MazeGUI() {
        setTitle("Maze Solver Using BFS, DFS, and A*");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        grid = new Cell[ROWS][COLS];
        initializeGrid();

        generateButton = new JButton("Generate Maze");
        solveBFSButton = new JButton("Solve BFS");
        solveDFSButton = new JButton("Solve DFS");
        solveAStarButton = new JButton("Solve A*");
        resetButton = new JButton("Reset Maze");
        speedSlider = new JSlider(1, 10, speed);
        timerLabel = new JLabel("Time: 0.000 s");

        JPanel controlPanel = new JPanel();
        controlPanel.add(generateButton);
        controlPanel.add(solveBFSButton);
        controlPanel.add(solveDFSButton);
        controlPanel.add(solveAStarButton);
        controlPanel.add(resetButton);
        controlPanel.add(new JLabel("Speed"));
        controlPanel.add(speedSlider);
        controlPanel.add(timerLabel);

        add(controlPanel, BorderLayout.SOUTH);

        mazePanel = new MazePanel();
        mazePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!mazeGenerated) return;

                int col = e.getX() / CELL_SIZE;
                int row = e.getY() / CELL_SIZE;

                if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return;
                Cell clicked = grid[row][col];

                if (selectingStart) {
                    start = clicked;
                    selectingStart = false;
                    selectingEnd = true;
                    repaint();
                    return;
                }

                if (selectingEnd) {
                    end = clicked;
                    selectingEnd = false;
                    repaint();
                }
            }
        });

        add(mazePanel, BorderLayout.CENTER);

        generateButton.addActionListener(e -> generateMaze());
        solveBFSButton.addActionListener(e -> solveMaze(true));
        solveDFSButton.addActionListener(e -> solveMaze(false));
        solveAStarButton.addActionListener(e -> solveAStar());
        resetButton.addActionListener(e -> resetMaze());
        speedSlider.addChangeListener(e -> speed = speedSlider.getValue());

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initializeGrid() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                grid[r][c] = new Cell(r, c);
    }

    private void generateMaze() {
        for (Cell[] row : grid)
            for (Cell c : row)
                c.resetAll();

        Random rand = new Random();
        Stack<Cell> stack = new Stack<>();

        Cell current = grid[rand.nextInt(ROWS)][rand.nextInt(COLS)];
        current.visitedGen = true;
        stack.push(current);

        while (!stack.isEmpty()) {
            current = stack.peek();
            Cell next = getUnvisitedNeighbor(current);

            if (next != null) {
                next.visitedGen = true;
                removeWall(current, next);
                stack.push(next);
            } else {
                stack.pop();
            }
        }

        start = null;
        end = null;
        selectingStart = true;
        selectingEnd = false;

        mazeGenerated = true;
        repaint();
    }

    private Cell getUnvisitedNeighbor(Cell cell) {
        List<Cell> neighbors = new ArrayList<>();
        for (Cell n : cell.getNeighbors(grid))
            if (!n.visitedGen) neighbors.add(n);

        if (neighbors.isEmpty()) return null;
        return neighbors.get(new Random().nextInt(neighbors.size()));
    }

    private void removeWall(Cell a, Cell b) {
        if (a.row == b.row + 1) { a.walls[0] = false; b.walls[2] = false; }
        else if (a.row == b.row - 1) { a.walls[2] = false; b.walls[0] = false; }
        else if (a.col == b.col + 1) { a.walls[3] = false; b.walls[1] = false; }
        else if (a.col == b.col - 1) { a.walls[1] = false; b.walls[3] = false; }
    }

    private void solveMaze(boolean bfs) {
        if (!mazeGenerated || start == null || end == null) return;

        resetPath();
        startTimer();

        SwingWorker<Void, Cell> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {

                Set<Cell> visited = new HashSet<>();
                Map<Cell, Cell> parent = new HashMap<>();

                Queue<Cell> queue = bfs ? new LinkedList<>() : null;
                Stack<Cell> stack = bfs ? null : new Stack<>();

                if (bfs) queue.add(start);
                else stack.push(start);

                visited.add(start);

                while ((bfs && !queue.isEmpty()) || (!bfs && !stack.isEmpty())) {
                    Cell current = bfs ? queue.poll() : stack.pop();

                    if (current == end)
                        break;

                    for (Cell neighbor : current.getNeighbors(grid)) {
                        if (current.hasWallBetween(neighbor)) continue;
                        if (visited.contains(neighbor)) continue;

                        visited.add(neighbor);
                        parent.put(neighbor, current);

                        neighbor.path = true;
                        publish(neighbor);
                        sleep(20 / speed);

                        if (bfs) queue.add(neighbor);
                        else stack.push(neighbor);
                    }
                }

                Cell step = end;
                while (step != null) {
                    step.path = true;
                    publish(step);
                    sleep(10 / speed);
                    step = parent.get(step);
                }

                return null;
            }

            @Override
            protected void process(List<Cell> chunks) {
                repaint();
            }

            @Override
            protected void done() {
                stopTimer();
            }
        };

        worker.execute();
    }

    private void solveAStar() {
        if (!mazeGenerated || start == null || end == null) return;

        resetPath();
        startTimer();

        SwingWorker<Void, Cell> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                Set<Cell> closed = new HashSet<>();
                Map<Cell, Cell> parent = new HashMap<>();
                Map<Cell, Integer> g = new HashMap<>();
                g.put(start, 0);

                PriorityQueue<Cell> open = new PriorityQueue<>(
                        Comparator.comparingInt(a -> g.get(a) + heuristic(a, end))
                );

                open.add(start);

                while (!open.isEmpty()) {
                    Cell current = open.poll();

                    if (current == end) break;
                    closed.add(current);

                    for (Cell neighbor : current.getNeighbors(grid)) {
                        if (current.hasWallBetween(neighbor)) continue;
                        if (closed.contains(neighbor)) continue;

                        int newG = g.get(current) + 1;

                        if (!g.containsKey(neighbor) || newG < g.get(neighbor)) {
                            g.put(neighbor, newG);
                            parent.put(neighbor, current);

                            neighbor.path = true;
                            publish(neighbor);
                            sleep(20 / speed);

                            open.add(neighbor);
                        }
                    }
                }

                Cell step = end;
                while (step != null) {
                    step.path = true;
                    publish(step);
                    sleep(10 / speed);
                    step = parent.get(step);
                }

                return null;
            }

            @Override
            protected void process(List<Cell> chunks) {
                repaint();
            }

            @Override
            protected void done() {
                stopTimer();
            }
        };

        worker.execute();
    }

    private int heuristic(Cell a, Cell b) {
        return Math.abs(a.row - b.row) + Math.abs(a.col - b.col);
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (Exception ignored) {}
    }

    private void resetPath() {
        for (Cell[] row : grid)
            for (Cell c : row)
                c.path = false;
        timerLabel.setText("Time: 0.000 s");
    }

    private void resetMaze() {
        mazeGenerated = false;
        start = null;
        end = null;
        selectingStart = true;
        selectingEnd = false;

        for (Cell[] row : grid)
            for (Cell c : row)
                c.resetAll();

        timerLabel.setText("Time: 0.000 s");

        if (timer != null && timer.isRunning())
            timer.stop();

        repaint();
    }
    private void startTimer() {
        startTime = System.currentTimeMillis();

        if (timer != null && timer.isRunning()) timer.stop();

        timer = new javax.swing.Timer(10, e -> { // update every 10 ms
            long elapsed = System.currentTimeMillis() - startTime;
            long seconds = elapsed / 1000;
            long millis = elapsed % 1000;
            timerLabel.setText(String.format("Time: %d.%03d s", seconds, millis));
        });
        timer.start();
    }

    private void stopTimer() {
        if (timer != null) timer.stop();
    }

    class MazePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            for (Cell[] row : grid)
                for (Cell c : row)
                    c.draw(g);

            if (start != null) {
                g.setColor(Color.GREEN);
                g.fillRect(start.col * CELL_SIZE + 3, start.row * CELL_SIZE + 3, CELL_SIZE - 6, CELL_SIZE - 6);
            }

            if (end != null) {
                g.setColor(Color.BLUE);
                g.fillRect(end.col * CELL_SIZE + 3, end.row * CELL_SIZE + 3, CELL_SIZE - 6, CELL_SIZE - 6);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(WIDTH, HEIGHT);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MazeGUI::new);
    }
}

class Cell {
    int row, col;

    boolean visitedGen = false;
    boolean path = false;

    boolean[] walls = { true, true, true, true };

    private final int SIZE = 20;

    Cell(int r, int c) {
        row = r;
        col = c;
    }

    void resetAll() {
        visitedGen = false;
        path = false;
        walls = new boolean[]{true, true, true, true};
    }

    void draw(Graphics g) {
        int x = col * SIZE;
        int y = row * SIZE;

        g.setColor(Color.WHITE);
        g.fillRect(x, y, SIZE, SIZE);

        if (path) {
            g.setColor(Color.RED);
            g.fillRect(x + 3, y + 3, SIZE - 6, SIZE - 6);
        }
        g.setColor(Color.BLACK);
        if (walls[0]) g.drawLine(x, y, x + SIZE, y);
        if (walls[1]) g.drawLine(x + SIZE, y, x + SIZE, y + SIZE);
        if (walls[2]) g.drawLine(x, y + SIZE, x + SIZE, y + SIZE);
        if (walls[3]) g.drawLine(x, y, x, y + SIZE);
    }
    List<Cell> getNeighbors(Cell[][] grid) {
        List<Cell> neighbors = new ArrayList<>();
        if (row > 0) neighbors.add(grid[row - 1][col]);
        if (col < grid[0].length - 1) neighbors.add(grid[row][col + 1]);
        if (row < grid.length - 1) neighbors.add(grid[row + 1][col]);
        if (col > 0) neighbors.add(grid[row][col - 1]);
        return neighbors;
    }
    boolean hasWallBetween(Cell o) {
        if (o.row == row && o.col == col - 1) return walls[3];
        if (o.row == row && o.col == col + 1) return walls[1];
        if (o.col == col && o.row == row - 1) return walls[0];
        if (o.col == col && o.row == row + 1) return walls[2];
        return true;
    }
}
