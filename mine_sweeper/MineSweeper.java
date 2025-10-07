import java.util.*;

public class MineSweeper {
    public static void main(String[] args) {
        System.out.println("xx");
        MineSweeper game = new MineSweeper(5, 5, 3);
        Scanner sc = new Scanner(System.in);
        while(true) {
            System.out.print("> ");
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("q")) {
                System.out.println("Bye.");
                break;
            }
            String[] parts = line.split("\\s+");
            int r = Integer.parseInt(parts[0]);
            int c = Integer.parseInt(parts[1]);
            game.click(r, c);
        }
    }

    private final int rows;
    private final int cols;
    private final char[][] board;
    private final int totalNumOfMine;
    private final Set<Integer> markedMine;
    private final Set<Integer> preSetMine;
    private final int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    public MineSweeper(int rows, int cols, int mines) {
        if(mines <= 0 || rows <=0 || cols <= 0 || mines >= rows * cols) throw new IllegalArgumentException("Input value is invalid."); 
        this.rows = rows;
        this.cols = cols;
        this.board = new char[rows][cols];
        this.totalNumOfMine = mines;
        for(char[] row : board){
            Arrays.fill(row, 'E');
        }
        int n = rows * cols;
        this.markedMine = new HashSet<>();
        this.preSetMine = new HashSet<>();
        Random r = new Random();
        while(preSetMine.size() < totalNumOfMine) {
            int pos = r.nextInt(n);
            if(preSetMine.contains(pos)) continue;
            preSetMine.add(pos);
            board[pos/cols][pos%cols] = 'M';
        }
        display();
    }

    public void click(int r, int c){
        if(r < 0 || c < 0 || r >= rows || c >= cols) throw new IllegalArgumentException("Input value is invalid."); 
        if(board[r][c] == 'B' || (board[r][c] >= '1' && board[r][c] <='8') || board[r][c] == 'O') {
            System.out.println(board[r][c] + " - Already uncovered this position, please enter another position.");
            return;
        }
        dfs(r, c);
        display();
    }

    private void dfs(int r, int c) {
        if(board[r][c] == 'M') {
            board[r][c] = 'X';
            System.out.println("Game Over!");
            return;
        }

        if(board[r][c] == 'B') {
            return;
        }

        if(board[r][c] >= '1' && board[r][c] <= '8') {
            return;
        }

        if(board[r][c] == 'E') {
            int cnt = countMine(r, c);
            if(cnt == 0) {
                board[r][c] = 'B';
                for(int[] dir : dirs) {
                    int nr = dir[0] + r;
                    int nc = dir[1] + c;
                    if(nr < 0 || nc < 0 || nr >= board.length || nc >= board[0].length) continue;
                    dfs(nr, nc);
                }
            }else{
                board[r][c] = (char) (cnt + '0');
            }
        }
    }

    private int countMine(int r, int c){
        int cnt = 0;
        for(int[] dir : dirs){
            int nr = dir[0] + r;
            int nc = dir[1] + c;
            if(nr < 0 || nc < 0 || nr >= rows || nc >= cols) continue;
            if(board[nr][nc] == 'M') cnt++;
        }
        return cnt;
    }

    public void display() {
        System.out.println("==== start ====");
        for(char[] row : board){
            System.out.println(Arrays.toString(row));
        }
        System.out.println("==== end   ====");
        System.out.println(" ");
        System.out.println(" ");
    }

    public void markMine(int r, int c){
        int pos = r * cols + c;
        if(board[r][c] != 'E') {
            System.out.println("cannot mark mine on an uncovered position.");
            return;
        }
        board[r][c] = 'O';
        if(preSetMine.contains(pos)) {
            markedMine.add(pos);
        }
        if(markedMine.size() == preSetMine.size()) {
            System.out.println("You Win!");
        }
    }
}
