package com.gomoku;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;

public class BoardPanel extends JPanel {

    private static final int MARGIN = 40;
    private static final int CELL_SIZE = 40;
    private static final int PIECE_RADIUS = 16;
    private static final int BOARD_PX = MARGIN * 2 + CELL_SIZE * (GameLogic.BOARD_SIZE - 1);

    private final GameLogic game;
    private final JLabel statusLabel;
    private Robot robot;
    private boolean waitingForAi = false;
    private NetworkPlay networkPlay;
    private int localPlayer;
    private String opponentName;
    private Runnable onChange;

    public BoardPanel(GameLogic game, JLabel statusLabel) {
        this.game = game;
        this.statusLabel = statusLabel;
        setPreferredSize(new Dimension(BOARD_PX, BOARD_PX));
        setBackground(new Color(220, 179, 92));
        updateStatus();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (game.isGameOver() || waitingForAi) return;
                if (localPlayer != 0 && game.getCurrentPlayer() != localPlayer) return;

                int col = Math.round((float) (e.getX() - MARGIN) / CELL_SIZE);
                int row = Math.round((float) (e.getY() - MARGIN) / CELL_SIZE);
                if (game.placePiece(row, col)) {
                    repaint();
                    updateStatus();
                    if (networkPlay != null) {
                        networkPlay.sendMove(row, col);
                    } else {
                        triggerAiMove();
                    }
                }
            }
        });
    }

    public void setOnChange(Runnable r) { this.onChange = r; }
    public void updateStatusPublic() { updateStatus(); }
    public void setRobot(Robot robot) { this.robot = robot; }

    public void setNetworkPlay(NetworkPlay net, int localPlayer) {
        this.networkPlay = net;
        this.localPlayer = localPlayer;
    }

    public void setOpponentName(String name) {
        this.opponentName = name;
        updateStatus();
    }

    public void triggerFirstAiMove() { triggerAiMove(); }

    public void receiveRemoteMove(int row, int col) {
        SwingUtilities.invokeLater(() -> {
            if (game.isGameOver()) return;
            game.placePiece(row, col);
            repaint();
            updateStatus();
        });
    }

    public void receiveRemoteReset() {
        SwingUtilities.invokeLater(() -> {
            game.reset();
            repaint();
            updateStatus();
        });
    }

    private void triggerAiMove() {
        if (robot == null || game.isGameOver()) return;
        waitingForAi = true;
        updateStatus();
        Timer timer = new Timer(200, e -> {
            int[] move = robot.bestMove(game);
            game.placePiece(move[0], move[1]);
            waitingForAi = false;
            repaint();
            updateStatus();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void updateStatus() {
        if (game.isGameOver()) {
            if (game.getWinner() == GameLogic.BLACK) {
                statusLabel.setText("黑棋胜！");
            } else if (game.getWinner() == GameLogic.WHITE) {
                statusLabel.setText("白棋胜！");
            } else {
                statusLabel.setText("平局！");
            }
        } else {
            String turn = game.getCurrentPlayer() == GameLogic.BLACK ? "黑棋" : "白棋";
            if (robot != null && waitingForAi) {
                statusLabel.setText(turn + "思考中...");
            } else if (localPlayer != 0 && game.getCurrentPlayer() != localPlayer) {
                String who = opponentName != null ? opponentName : "对手";
                statusLabel.setText("等待 " + who + " 落子...");
            } else {
                statusLabel.setText(turn + "落子");
            }
        }
        if (onChange != null) onChange.run();
    }

    public void resetGame() {
        game.reset();
        waitingForAi = false;
        if (networkPlay != null) networkPlay.sendReset();
        updateStatus();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawGrid(g2);
        drawStarPoints(g2);
        drawPieces(g2);
        drawLastMove(g2);
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < GameLogic.BOARD_SIZE; i++) {
            int pos = MARGIN + i * CELL_SIZE;
            g2.drawLine(MARGIN, pos, MARGIN + (GameLogic.BOARD_SIZE - 1) * CELL_SIZE, pos);
            g2.drawLine(pos, MARGIN, pos, MARGIN + (GameLogic.BOARD_SIZE - 1) * CELL_SIZE);
        }
    }

    private void drawStarPoints(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        int[] stars = {3, 7, 11};
        for (int r : stars) {
            for (int c : stars) {
                int x = MARGIN + c * CELL_SIZE;
                int y = MARGIN + r * CELL_SIZE;
                g2.fillOval(x - 4, y - 4, 8, 8);
            }
        }
    }

    private void drawPieces(Graphics2D g2) {
        for (int r = 0; r < GameLogic.BOARD_SIZE; r++) {
            for (int c = 0; c < GameLogic.BOARD_SIZE; c++) {
                int piece = game.getPiece(r, c);
                if (piece == GameLogic.EMPTY) continue;

                int cx = MARGIN + c * CELL_SIZE;
                int cy = MARGIN + r * CELL_SIZE;
                Ellipse2D oval = new Ellipse2D.Float(
                        cx - PIECE_RADIUS, cy - PIECE_RADIUS,
                        PIECE_RADIUS * 2, PIECE_RADIUS * 2);

                if (piece == GameLogic.BLACK) {
                    g2.setPaint(new GradientPaint(
                            cx - PIECE_RADIUS, cy - PIECE_RADIUS, new Color(60, 60, 60),
                            cx + PIECE_RADIUS, cy + PIECE_RADIUS, Color.BLACK));
                } else {
                    g2.setPaint(new GradientPaint(
                            cx - PIECE_RADIUS, cy - PIECE_RADIUS, Color.WHITE,
                            cx + PIECE_RADIUS, cy + PIECE_RADIUS, new Color(200, 200, 200)));
                }
                g2.fill(oval);
                g2.setColor(new Color(0, 0, 0, 80));
                g2.setStroke(new BasicStroke(1.0f));
                g2.draw(oval);
            }
        }
    }

    private void drawLastMove(Graphics2D g2) {
        int lr = game.getLastRow(), lc = game.getLastCol();
        if (lr < 0) return;
        int cx = MARGIN + lc * CELL_SIZE;
        int cy = MARGIN + lr * CELL_SIZE;
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(2.0f));
        int s = 5;
        g2.drawLine(cx - s, cy, cx + s, cy);
        g2.drawLine(cx, cy - s, cx, cy + s);
    }
}
