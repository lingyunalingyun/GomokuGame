package com.gomoku;

public class GameLogic {

    public static final int BOARD_SIZE = 15;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private int currentPlayer = BLACK;
    private boolean gameOver = false;
    private int winner = EMPTY;
    private int lastRow = -1, lastCol = -1;

    public boolean placePiece(int row, int col) {
        if (gameOver || row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return false;
        if (board[row][col] != EMPTY) return false;

        board[row][col] = currentPlayer;
        lastRow = row;
        lastCol = col;

        if (checkWin(row, col, currentPlayer)) {
            gameOver = true;
            winner = currentPlayer;
        } else if (isBoardFull()) {
            gameOver = true;
        } else {
            currentPlayer = (currentPlayer == BLACK) ? WHITE : BLACK;
        }
        return true;
    }

    private boolean checkWin(int row, int col, int player) {
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : directions) {
            int count = 1;
            count += countInDirection(row, col, d[0], d[1], player);
            count += countInDirection(row, col, -d[0], -d[1], player);
            if (count >= 5) return true;
        }
        return false;
    }

    private int countInDirection(int row, int col, int dr, int dc, int player) {
        int count = 0;
        int r = row + dr, c = col + dc;
        while (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE && board[r][c] == player) {
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }

    private boolean isBoardFull() {
        for (int[] row : board) {
            for (int cell : row) {
                if (cell == EMPTY) return false;
            }
        }
        return true;
    }

    public void reset() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = EMPTY;
            }
        }
        currentPlayer = BLACK;
        gameOver = false;
        winner = EMPTY;
        lastRow = lastCol = -1;
    }

    public void surrender(int loser) {
        if (gameOver) return;
        gameOver = true;
        winner = (loser == BLACK) ? WHITE : BLACK;
    }

    public int getPiece(int row, int col) { return board[row][col]; }
    public int getCurrentPlayer() { return currentPlayer; }
    public boolean isGameOver() { return gameOver; }
    public int getWinner() { return winner; }
    public int getLastRow() { return lastRow; }
    public int getLastCol() { return lastCol; }
}
