package com.gomoku;

/**
 * AI 机器人类（人机对战的对手）
 * 使用评分算法遍历空位，对每个位置在四方向评估攻防价值
 * 评分规则：活一=10、活二=100、活三=5000、活四=100000、五连=1000000
 * bestMove() 综合攻击和防守分数选出最优落子位置
 */
public class Robot {

    private final int aiPlayer;

    public Robot(int aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    public int getAiPlayer() { return aiPlayer; }

    public int[] bestMove(GameLogic game) {
        int bestRow = -1, bestCol = -1;
        int bestScore = -1;
        int opponent = (aiPlayer == GameLogic.BLACK) ? GameLogic.WHITE : GameLogic.BLACK;

        for (int r = 0; r < GameLogic.BOARD_SIZE; r++) {
            for (int c = 0; c < GameLogic.BOARD_SIZE; c++) {
                if (game.getPiece(r, c) != GameLogic.EMPTY) continue;
                if (!hasNeighbor(game, r, c)) continue;

                int attack = evaluatePosition(game, r, c, aiPlayer);
                int defense = evaluatePosition(game, r, c, opponent);
                int score = Math.max(attack, defense) * 2 + Math.min(attack, defense);
                if (attack >= 100000) score = attack * 10;
                if (defense >= 100000) score = Math.max(score, defense * 5);

                if (score > bestScore) {
                    bestScore = score;
                    bestRow = r;
                    bestCol = c;
                }
            }
        }

        if (bestRow == -1) {
            bestRow = GameLogic.BOARD_SIZE / 2;
            bestCol = GameLogic.BOARD_SIZE / 2;
        }
        return new int[]{bestRow, bestCol};
    }

    private boolean hasNeighbor(GameLogic game, int row, int col) {
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr, c = col + dc;
                if (r >= 0 && r < GameLogic.BOARD_SIZE && c >= 0 && c < GameLogic.BOARD_SIZE
                        && game.getPiece(r, c) != GameLogic.EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }

    private int evaluatePosition(GameLogic game, int row, int col, int player) {
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        int totalScore = 0;
        for (int[] d : directions) {
            totalScore += evaluateLine(game, row, col, d[0], d[1], player);
        }
        return totalScore;
    }

    private int evaluateLine(GameLogic game, int row, int col, int dr, int dc, int player) {
        int count = 1;
        boolean openHead = false, openTail = false;

        int r = row + dr, c = col + dc;
        while (inBounds(r, c) && game.getPiece(r, c) == player) {
            count++;
            r += dr;
            c += dc;
        }
        if (inBounds(r, c) && game.getPiece(r, c) == GameLogic.EMPTY) openHead = true;

        r = row - dr;
        c = col - dc;
        while (inBounds(r, c) && game.getPiece(r, c) == player) {
            count++;
            r -= dr;
            c -= dc;
        }
        if (inBounds(r, c) && game.getPiece(r, c) == GameLogic.EMPTY) openTail = true;

        int openEnds = (openHead ? 1 : 0) + (openTail ? 1 : 0);
        if (openEnds == 0 && count < 5) return 0;

        return switch (count) {
            case 1 -> openEnds == 2 ? 10 : 1;
            case 2 -> openEnds == 2 ? 100 : 10;
            case 3 -> openEnds == 2 ? 5000 : 500;
            case 4 -> openEnds == 2 ? 100000 : 10000;
            default -> 1000000;
        };
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < GameLogic.BOARD_SIZE && c >= 0 && c < GameLogic.BOARD_SIZE;
    }
}
