package com.gomoku;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;

/**
 * 棋盘面板（继承 JPanel）
 * 负责棋盘绘制（仿木纹渐变背景、棋子 RadialGradientPaint 3D 光影效果）、
 * 鼠标交互（点击落子、悬停预览）、AI 触发和网络同步
 * 重写 paintComponent() 实现自定义绘图
 */
public class BoardPanel extends JPanel {

    private static final int MARGIN = 40;
    private static final int CELL_SIZE = 40;
    private static final int PIECE_RADIUS = 17;
    private static final int BOARD_PX = MARGIN * 2 + CELL_SIZE * (GameLogic.BOARD_SIZE - 1);
    private int hoverRow = -1, hoverCol = -1;

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
            @Override
            public void mouseExited(MouseEvent e) { hoverRow = hoverCol = -1; repaint(); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int c = Math.round((float) (e.getX() - MARGIN) / CELL_SIZE);
                int r = Math.round((float) (e.getY() - MARGIN) / CELL_SIZE);
                if (r >= 0 && r < GameLogic.BOARD_SIZE && c >= 0 && c < GameLogic.BOARD_SIZE) {
                    if (r != hoverRow || c != hoverCol) { hoverRow = r; hoverCol = c; repaint(); }
                } else if (hoverRow >= 0) { hoverRow = hoverCol = -1; repaint(); }
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
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        drawBoard(g2);
        drawGrid(g2);
        drawStarPoints(g2);
        drawHover(g2);
        drawPieces(g2);
        drawLastMove(g2);
    }

    private void drawBoard(Graphics2D g2) {
        int bx = MARGIN - 20, by = MARGIN - 20;
        int bw = (GameLogic.BOARD_SIZE - 1) * CELL_SIZE + 40;
        g2.setPaint(new GradientPaint(bx, by, new Color(235, 195, 120), bx + bw, by + bw, new Color(210, 170, 90)));
        g2.fillRoundRect(bx, by, bw, bw, 6, 6);
        g2.setColor(new Color(140, 100, 50));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawRoundRect(bx, by, bw, bw, 6, 6);
        g2.setColor(new Color(160, 120, 60));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(bx + 4, by + 4, bw - 8, bw - 8, 4, 4);
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(new Color(80, 55, 30));
        g2.setStroke(new BasicStroke(0.9f));
        int end = MARGIN + (GameLogic.BOARD_SIZE - 1) * CELL_SIZE;
        for (int i = 0; i < GameLogic.BOARD_SIZE; i++) {
            int pos = MARGIN + i * CELL_SIZE;
            g2.drawLine(MARGIN, pos, end, pos);
            g2.drawLine(pos, MARGIN, pos, end);
        }
    }

    private void drawStarPoints(Graphics2D g2) {
        g2.setColor(new Color(60, 40, 20));
        int[] stars = {3, 7, 11};
        for (int r : stars) {
            for (int c : stars) {
                int x = MARGIN + c * CELL_SIZE;
                int y = MARGIN + r * CELL_SIZE;
                g2.fillOval(x - 4, y - 4, 8, 8);
            }
        }
    }

    private void drawHover(Graphics2D g2) {
        if (hoverRow < 0 || game.isGameOver() || waitingForAi) return;
        if (localPlayer != 0 && game.getCurrentPlayer() != localPlayer) return;
        if (game.getPiece(hoverRow, hoverCol) != GameLogic.EMPTY) return;
        int cx = MARGIN + hoverCol * CELL_SIZE;
        int cy = MARGIN + hoverRow * CELL_SIZE;
        int r = PIECE_RADIUS;
        Color c = game.getCurrentPlayer() == GameLogic.BLACK ? new Color(0, 0, 0, 50) : new Color(255, 255, 255, 70);
        g2.setColor(c);
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    private void drawPieces(Graphics2D g2) {
        for (int r = 0; r < GameLogic.BOARD_SIZE; r++) {
            for (int c = 0; c < GameLogic.BOARD_SIZE; c++) {
                int piece = game.getPiece(r, c);
                if (piece == GameLogic.EMPTY) continue;

                int cx = MARGIN + c * CELL_SIZE;
                int cy = MARGIN + r * CELL_SIZE;
                int pr = PIECE_RADIUS;

                Ellipse2D shadow = new Ellipse2D.Float(cx - pr + 2, cy - pr + 2, pr * 2, pr * 2);
                g2.setColor(new Color(0, 0, 0, 40));
                g2.fill(shadow);

                Ellipse2D oval = new Ellipse2D.Float(cx - pr, cy - pr, pr * 2, pr * 2);

                if (piece == GameLogic.BLACK) {
                    g2.setPaint(new RadialGradientPaint(
                            cx - pr * 0.3f, cy - pr * 0.3f, pr * 1.8f,
                            new float[]{0f, 1f},
                            new Color[]{new Color(80, 80, 80), new Color(10, 10, 10)}));
                } else {
                    g2.setPaint(new RadialGradientPaint(
                            cx - pr * 0.3f, cy - pr * 0.3f, pr * 1.8f,
                            new float[]{0f, 1f},
                            new Color[]{new Color(255, 255, 255), new Color(190, 190, 185)}));
                }
                g2.fill(oval);

                Ellipse2D highlight = new Ellipse2D.Float(cx - pr * 0.45f, cy - pr * 0.5f, pr * 0.6f, pr * 0.4f);
                g2.setColor(piece == GameLogic.BLACK ? new Color(255, 255, 255, 35) : new Color(255, 255, 255, 120));
                g2.fill(highlight);

                g2.setColor(new Color(0, 0, 0, 60));
                g2.setStroke(new BasicStroke(0.8f));
                g2.draw(oval);
            }
        }
    }

    private void drawLastMove(Graphics2D g2) {
        int lr = game.getLastRow(), lc = game.getLastCol();
        if (lr < 0) return;
        int cx = MARGIN + lc * CELL_SIZE;
        int cy = MARGIN + lr * CELL_SIZE;
        int piece = game.getPiece(lr, lc);
        g2.setColor(piece == GameLogic.BLACK ? new Color(255, 80, 80) : new Color(220, 50, 50));
        g2.setStroke(new BasicStroke(2f));
        int s = 5;
        g2.drawLine(cx - s, cy, cx + s, cy);
        g2.drawLine(cx, cy - s, cx, cy + s);
    }
}
