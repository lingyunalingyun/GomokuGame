package com.gomoku;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class LobbyFrame extends JFrame {

    private static final String AVATAR_API = "https://musetreehouse.com/api/avatar.php?f=";

    private UserInfo currentUser = UserInfo.GUEST;
    private Image avatarImage;
    private JLabel avatarName;
    private JLabel infoLevel;
    private JLabel infoRole;
    private JButton authButton;
    private JPanel avatarCircle;
    private JButton rejoinBtn;
    private int activeRoomId;
    private int activeRoomPlayer;

    public LobbyFrame() {
        super("五子棋");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(null);
        tryAutoLogin();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(24, 28, 36));
        root.setPreferredSize(new Dimension(620, 475));

        root.add(buildTitle(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(0, 30, 20, 30));
        body.add(buildModesPanel(), BorderLayout.CENTER);
        body.add(buildUserPanel(), BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);

        setContentPane(root);
    }

    private JPanel buildTitle() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(22, 0, 10, 0));
        JLabel t = new JLabel("五 子 棋");
        t.setFont(new Font("Microsoft YaHei", Font.BOLD, 30));
        t.setForeground(new Color(230, 230, 230));
        p.add(t);
        return p;
    }

    // ───────── 左侧：模式选择 ─────────

    private JPanel buildModesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        JLabel header = new JLabel("游戏模式");
        header.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        header.setForeground(new Color(180, 180, 180));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(header);
        panel.add(Box.createVerticalStrut(10));

        rejoinBtn = modeButton("⚔ 回到对局", "你有一场正在进行的在线对局");
        rejoinBtn.setBackground(new Color(50, 45, 20));
        rejoinBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 140, 40), 1, true),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        rejoinBtn.setVisible(false);
        rejoinBtn.addActionListener(e -> rejoinOnlineRoom());
        rejoinBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { rejoinBtn.setBackground(new Color(65, 58, 28)); }
            @Override public void mouseExited(MouseEvent e) { rejoinBtn.setBackground(new Color(50, 45, 20)); }
        });
        panel.add(rejoinBtn);
        panel.add(Box.createVerticalStrut(7));

        String[][] modes = {
                {"真人对战", "两人轮流在同一台电脑上对弈"},
                {"人机对战（你执黑）", "挑战 AI，你先手"},
                {"人机对战（你执白）", "挑战 AI，AI 先手"},
                {"局域网对战", "与局域网内的好友联机对弈"},
                {"在线对战", "通过服务器与远程玩家对弈"},
        };
        for (int i = 0; i < modes.length; i++) {
            JButton btn = modeButton(modes[i][0], modes[i][1]);
            final int m = i;
            btn.addActionListener(e -> onMode(m));
            panel.add(btn);
            panel.add(Box.createVerticalStrut(7));
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JButton modeButton(String title, String desc) {
        JButton btn = new JButton(
                "<html><b style='font-size:11px'>" + title
                        + "</b><br><span style='font-size:9px;color:#888'>" + desc + "</span></html>");
        btn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBackground(new Color(35, 40, 52));
        btn.setForeground(new Color(220, 220, 220));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 60, 75), 1, true),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(320, 54));
        btn.setPreferredSize(new Dimension(320, 54));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.addMouseListener(new MouseAdapter() {
            final Color normal = new Color(35, 40, 52);
            final Color hover = new Color(50, 58, 72);
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(hover); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(normal); }
        });
        return btn;
    }

    // ───────── 右侧：个人信息 ─────────

    private JPanel buildUserPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(190, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 55, 65)),
                BorderFactory.createEmptyBorder(0, 22, 0, 0)));

        JLabel header = new JLabel("个人信息");
        header.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        header.setForeground(new Color(180, 180, 180));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(header);
        panel.add(Box.createVerticalStrut(18));

        avatarCircle = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Shape circle = new Ellipse2D.Float(0, 0, 60, 60);
                if (avatarImage != null) {
                    g2.setClip(circle);
                    g2.drawImage(avatarImage, 0, 0, 60, 60, null);
                    g2.setClip(null);
                    g2.setColor(new Color(60, 65, 80));
                    g2.setStroke(new BasicStroke(2));
                    g2.draw(circle);
                } else {
                    g2.setColor(avatarColor());
                    g2.fill(circle);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 24));
                    String ch = currentUser.isGuest() ? "?" : currentUser.username().substring(0, 1).toUpperCase();
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(ch, (60 - fm.stringWidth(ch)) / 2, (60 - fm.getHeight()) / 2 + fm.getAscent());
                }
            }
        };
        avatarCircle.setOpaque(false);
        avatarCircle.setPreferredSize(new Dimension(60, 60));
        avatarCircle.setMaximumSize(new Dimension(60, 60));
        avatarCircle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(avatarCircle);
        panel.add(Box.createVerticalStrut(12));

        avatarName = new JLabel();
        avatarName.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        avatarName.setForeground(new Color(230, 230, 230));
        avatarName.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(avatarName);
        panel.add(Box.createVerticalStrut(4));

        infoLevel = new JLabel();
        infoLevel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        infoLevel.setForeground(new Color(140, 140, 140));
        infoLevel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(infoLevel);

        infoRole = new JLabel();
        infoRole.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        infoRole.setForeground(new Color(140, 140, 140));
        infoRole.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(infoRole);

        panel.add(Box.createVerticalGlue());

        authButton = new JButton();
        authButton.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        authButton.setFocusPainted(false);
        authButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        authButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        authButton.setMaximumSize(new Dimension(120, 32));
        authButton.addActionListener(e -> onAuth());
        panel.add(authButton);

        refreshUserInfo();
        return panel;
    }

    private Color avatarColor() {
        return switch (currentUser.role()) {
            case "owner" -> new Color(248, 81, 73);
            case "admin" -> new Color(167, 139, 250);
            case "sponsor" -> new Color(210, 170, 60);
            default -> currentUser.isGuest() ? new Color(110, 110, 110) : new Color(88, 166, 255);
        };
    }

    private void refreshUserInfo() {
        if (currentUser.isGuest()) {
            avatarImage = null;
            avatarName.setText("游客");
            infoLevel.setText("未登录");
            infoRole.setText(" ");
            authButton.setText("登录");
        } else {
            avatarName.setText(currentUser.username());
            infoLevel.setText("Lv." + currentUser.level());
            infoRole.setText(switch (currentUser.role()) {
                case "owner" -> "站长";
                case "admin" -> "管理员";
                case "sponsor" -> "赞助者";
                default -> "用户";
            });
            authButton.setText("注销");
            loadAvatar(currentUser.avatar());
        }
        avatarCircle.repaint();
    }

    private void loadAvatar(String avatarFile) {
        if (avatarFile == null || avatarFile.isEmpty()) avatarFile = "default.png";
        String url = AVATAR_API + avatarFile;
        new Thread(() -> {
            try {
                BufferedImage img = ImageIO.read(new URI(url).toURL());
                if (img != null) SwingUtilities.invokeLater(() -> { avatarImage = img; avatarCircle.repaint(); });
            } catch (Exception ignored) {}
        }, "AvatarLoad").start();
    }

    private void tryAutoLogin() {
        String[] saved = CredentialStore.load();
        if (saved == null) return;
        new Thread(() -> {
            try {
                UserInfo info = AuthService.login(saved[0], saved[1]);
                SwingUtilities.invokeLater(() -> { currentUser = info; refreshUserInfo(); checkActiveRoom(); });
            } catch (Exception e) {
                CredentialStore.clear();
            }
        }, "AutoLogin").start();
    }

    private void onAuth() {
        if (currentUser.isGuest()) {
            UserInfo u = LoginDialog.show(this);
            if (u != null && !u.isGuest()) { currentUser = u; refreshUserInfo(); checkActiveRoom(); }
        } else {
            currentUser = UserInfo.GUEST;
            CredentialStore.clear();
            refreshUserInfo();
            rejoinBtn.setVisible(false);
        }
    }

    private void checkActiveRoom() {
        if (currentUser.isGuest()) { rejoinBtn.setVisible(false); return; }
        new Thread(() -> {
            String resp = OnlinePlay.get("my_room&user_id=" + currentUser.id());
            if (resp != null && resp.contains("\"room\":{")) {
                activeRoomId = OnlinePlay.extractInt(resp, "id");
                activeRoomPlayer = OnlinePlay.extractInt(resp, "player");
                SwingUtilities.invokeLater(() -> rejoinBtn.setVisible(true));
            } else {
                SwingUtilities.invokeLater(() -> rejoinBtn.setVisible(false));
            }
        }, "CheckRoom").start();
    }

    private void rejoinOnlineRoom() {
        GameLogic game = new GameLogic();
        OnlinePlay net = new OnlinePlay(activeRoomId, activeRoomPlayer);
        String opName = net.replay(game);
        int localPlayer = activeRoomPlayer == 1 ? GameLogic.BLACK : GameLogic.WHITE;
        String side = localPlayer == GameLogic.BLACK ? "黑" : "白";
        launchGame(null, net, localPlayer, "在线对战（你执" + side + "）", game, opName);
    }

    // ───────── 模式处理 ─────────

    private void onMode(int m) {
        switch (m) {
            case 0 -> launchGame(null, null, 0, "真人对战");
            case 1 -> launchGame(new Robot(GameLogic.WHITE), null, 0, "人机对战（你执黑）");
            case 2 -> launchGame(new Robot(GameLogic.BLACK), null, 0, "人机对战（你执白）");
            case 3 -> startLan();
            case 4 -> startOnline();
        }
    }

    // ───────── 在线对战 ─────────

    private void startOnline() {
        String[] sub = {"创建房间", "加入房间"};
        int c = JOptionPane.showOptionDialog(this, "在线对战", "五子棋",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, sub, sub[0]);
        if (c == 0) createOnlineRoom();
        else if (c == 1) joinOnlineRoom();
    }

    private void createOnlineRoom() {
        String def = currentUser.isGuest() ? "游客的房间" : currentUser.username() + "的房间";
        String name = JOptionPane.showInputDialog(this, "房间名称：", def);
        if (name == null || name.isBlank()) return;

        String displayName = currentUser.isGuest() ? "游客" : currentUser.displayName();
        String resp = OnlinePlay.post("create",
                "{\"name\":\"" + name.trim().replace("\"", "\\\"") + "\","
                + "\"user_id\":" + currentUser.id() + ","
                + "\"user_name\":\"" + displayName.replace("\"", "\\\"") + "\"}");
        if (resp == null || !resp.contains("\"success\":true")) {
            JOptionPane.showMessageDialog(this, "创建房间失败");
            return;
        }
        int roomId = OnlinePlay.extractInt(resp, "room_id");

        JDialog d = new JDialog(this, "等待对手", true);
        JLabel lb = new JLabel("<html><center>房间已创建，等待对手加入...<br>房间号: " + roomId + "</center></html>", SwingConstants.CENTER);
        lb.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        lb.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));
        JButton cancel = new JButton("取消");
        cancel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        JPanel bp = new JPanel();
        bp.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        bp.add(cancel);
        d.setLayout(new BorderLayout());
        d.add(lb, BorderLayout.CENTER);
        d.add(bp, BorderLayout.SOUTH);
        d.setSize(300, 150);
        d.setLocationRelativeTo(this);
        d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        AtomicBoolean ok = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();

        Runnable doCancel = () -> { cancelled.set(true); d.dispose(); };
        cancel.addActionListener(e -> doCancel.run());
        d.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { doCancel.run(); } });

        int rid = roomId;
        new Thread(() -> {
            while (!cancelled.get()) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                String poll = OnlinePlay.get("poll&room_id=" + rid + "&after=0");
                if (poll != null && poll.contains("\"status\":\"playing\"")) {
                    ok.set(true);
                    SwingUtilities.invokeLater(d::dispose);
                    break;
                }
            }
        }, "WaitOnline").start();

        d.setVisible(true);

        if (ok.get()) {
            OnlinePlay net = new OnlinePlay(roomId, 1);
            launchGame(null, net, GameLogic.BLACK, "在线对战（你执黑）");
        } else {
            OnlinePlay.post("quit", "{\"room_id\":" + roomId + ",\"player\":1}");
        }
    }

    private void joinOnlineRoom() {
        JDialog d = new JDialog(this, "在线房间", true);
        DefaultListModel<String> model = new DefaultListModel<>();
        java.util.List<int[]> roomIds = new java.util.ArrayList<>();
        JList<String> list = new JList<>(model);
        list.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        AtomicReference<Integer> selectedRoom = new AtomicReference<>();
        AtomicReference<String> selectedHost = new AtomicReference<>();
        AtomicBoolean alive = new AtomicBoolean(true);

        Runnable refreshList = () -> {
            String resp = OnlinePlay.get("list");
            if (resp == null || !resp.contains("\"success\":true")) return;
            SwingUtilities.invokeLater(() -> {
                int sel = list.getSelectedIndex();
                model.clear();
                roomIds.clear();
                int pos = 0;
                while (true) {
                    int s = resp.indexOf("{\"id\":", pos);
                    if (s < 0) break;
                    int e = resp.indexOf('}', s);
                    if (e < 0) break;
                    String obj = resp.substring(s, e + 1);
                    int id = OnlinePlay.extractInt(obj, "id");
                    String rname = OnlinePlay.extractStr(obj, "name");
                    String host = OnlinePlay.extractStr(obj, "host_name");
                    model.addElement(rname + "  —  " + host + "  #" + id);
                    roomIds.add(new int[]{id});
                    pos = e + 1;
                }
                if (sel >= 0 && sel < model.size()) list.setSelectedIndex(sel);
            });
        };

        new Thread(() -> {
            while (alive.get()) {
                refreshList.run();
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
            }
        }, "RoomRefresh").start();

        JLabel hint = new JLabel("  房间列表（每 2 秒刷新）");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        hint.setForeground(new Color(100, 100, 100));
        hint.setBorder(BorderFactory.createEmptyBorder(8, 5, 5, 5));

        JButton joinBtn = new JButton("加入");
        joinBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        joinBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0 || idx >= roomIds.size()) { JOptionPane.showMessageDialog(d, "请先选择一个房间"); return; }
            int rid = roomIds.get(idx)[0];
            String displayName = currentUser.isGuest() ? "游客" : currentUser.displayName();
            String resp = OnlinePlay.post("join",
                    "{\"room_id\":" + rid + ","
                    + "\"user_id\":" + currentUser.id() + ","
                    + "\"user_name\":\"" + displayName.replace("\"", "\\\"") + "\"}");
            if (resp == null || !resp.contains("\"success\":true")) {
                String err = OnlinePlay.extractStr(resp != null ? resp : "", "error");
                JOptionPane.showMessageDialog(d, err.isEmpty() ? "加入失败" : err);
                return;
            }
            selectedRoom.set(rid);
            selectedHost.set(OnlinePlay.extractStr(resp, "host_name"));
            alive.set(false);
            d.dispose();
        });

        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && list.getSelectedValue() != null) joinBtn.doClick(); }
        });

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        cancelBtn.addActionListener(e -> { alive.set(false); d.dispose(); });
        d.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { alive.set(false); } });

        JPanel bp = new JPanel();
        bp.setBorder(BorderFactory.createEmptyBorder(5, 0, 8, 0));
        bp.add(joinBtn);
        bp.add(cancelBtn);
        d.setLayout(new BorderLayout());
        d.add(hint, BorderLayout.NORTH);
        d.add(new JScrollPane(list), BorderLayout.CENTER);
        d.add(bp, BorderLayout.SOUTH);
        d.setSize(380, 320);
        d.setLocationRelativeTo(this);
        d.setVisible(true);

        Integer rid = selectedRoom.get();
        if (rid == null) return;
        OnlinePlay net = new OnlinePlay(rid, 2);
        launchGame(null, net, GameLogic.WHITE, "在线对战（你执白）");
    }

    // ───────── 局域网对战 ─────────

    private void startLan() {
        String[] sub = {"创建房间", "加入房间"};
        int c = JOptionPane.showOptionDialog(this, "局域网对战", "五子棋",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, sub, sub[0]);
        if (c == 0) createRoom();
        else if (c == 1) joinRoom();
    }

    private void createRoom() {
        String def = currentUser.isGuest() ? System.getProperty("user.name") + "的房间" : currentUser.username() + "的房间";
        String name = JOptionPane.showInputDialog(this, "房间名称：", def);
        if (name == null || name.isBlank()) return;

        String display = currentUser.isGuest() ? "游客" : currentUser.displayName();
        LanHost host;
        try { host = new LanHost(name.trim(), display); }
        catch (IOException e) { JOptionPane.showMessageDialog(this, "创建失败：" + e.getMessage()); return; }

        host.startBroadcast();

        JDialog d = new JDialog(this, "等待对手", true);
        JLabel lb = new JLabel("<html><center>等待对手加入...<br>" + host.getLocalIp() + " : " + host.getPort() + "</center></html>", SwingConstants.CENTER);
        lb.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        lb.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));
        JButton cancel = new JButton("取消");
        cancel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        JPanel bp = new JPanel();
        bp.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        bp.add(cancel);
        d.setLayout(new BorderLayout());
        d.add(lb, BorderLayout.CENTER);
        d.add(bp, BorderLayout.SOUTH);
        d.setSize(300, 150);
        d.setLocationRelativeTo(this);
        d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        AtomicBoolean ok = new AtomicBoolean();
        Runnable doCancel = () -> { host.cancelWait(); d.dispose(); };
        cancel.addActionListener(e -> doCancel.run());
        d.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { doCancel.run(); } });

        new Thread(() -> {
            try { host.waitForClient(); ok.set(true); SwingUtilities.invokeLater(d::dispose); }
            catch (IOException ignored) {}
        }, "WaitClient").start();

        d.setVisible(true);
        if (ok.get()) launchGame(null, host, GameLogic.BLACK, "局域网对战（你执黑）");
        else host.close();
    }

    private void joinRoom() {
        JDialog d = new JDialog(this, "搜索房间", true);
        DefaultListModel<LanGuest.RoomInfo> model = new DefaultListModel<>();
        JList<LanGuest.RoomInfo> list = new JList<>(model);
        list.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Set<String> seen = new HashSet<>();
        AtomicReference<LanGuest.RoomInfo> sel = new AtomicReference<>();

        DatagramSocket ds = LanGuest.startDiscovery(room -> SwingUtilities.invokeLater(() -> {
            if (seen.add(room.ip() + ":" + room.port())) model.addElement(room);
        }));

        JLabel hint = new JLabel("  正在搜索局域网内的房间...");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        hint.setForeground(new Color(100, 100, 100));
        hint.setBorder(BorderFactory.createEmptyBorder(8, 5, 5, 5));

        JButton joinBtn = new JButton("加入");
        joinBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        joinBtn.addActionListener(e -> {
            LanGuest.RoomInfo r = list.getSelectedValue();
            if (r == null) { JOptionPane.showMessageDialog(d, "请先选择一个房间"); return; }
            sel.set(r);
            if (ds != null) ds.close();
            d.dispose();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && list.getSelectedValue() != null) joinBtn.doClick(); }
        });

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        cancelBtn.addActionListener(e -> { if (ds != null) ds.close(); d.dispose(); });
        d.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { if (ds != null) ds.close(); } });

        JPanel bp = new JPanel();
        bp.setBorder(BorderFactory.createEmptyBorder(5, 0, 8, 0));
        bp.add(joinBtn);
        bp.add(cancelBtn);
        d.setLayout(new BorderLayout());
        d.add(hint, BorderLayout.NORTH);
        d.add(new JScrollPane(list), BorderLayout.CENTER);
        d.add(bp, BorderLayout.SOUTH);
        d.setSize(360, 300);
        d.setLocationRelativeTo(this);
        d.setVisible(true);

        LanGuest.RoomInfo room = sel.get();
        if (room == null) return;
        LanGuest guest = new LanGuest();
        try {
            guest.connect(room);
            launchGame(null, guest, GameLogic.WHITE, "局域网对战（你执白）");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "连接失败：" + e.getMessage());
            guest.close();
        }
    }

    // ───────── 启动对局 ─────────

    private void launchGame(Robot robot, NetworkPlay net, int localPlayer, String modeText) {
        launchGame(robot, net, localPlayer, modeText, null, null);
    }

    private void launchGame(Robot robot, NetworkPlay net, int localPlayer, String modeText,
                            GameLogic existingGame, String opponentName) {
        setVisible(false);

        GameLogic game = existingGame != null ? existingGame : new GameLogic();
        String title = currentUser.isGuest() ? "五子棋" : "五子棋 - " + currentUser.displayName();
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false);

        JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
        statusLabel.setPreferredSize(new Dimension(0, 40));

        BoardPanel boardPanel = new BoardPanel(game, statusLabel);
        if (robot != null) boardPanel.setRobot(robot);
        if (net != null) boardPanel.setNetworkPlay(net, localPlayer);

        // ── 玩家数据 ──
        String userName = currentUser.isGuest() ? "游客" : currentUser.username();
        String[] names = new String[2];
        Image[] avatars = new Image[2];
        int[] countdown = {30, 30};
        int[] totalTime = {0, 0};
        int[][] stats = new int[2][5]; // {total, wins, losses, streak, score}
        int[] autoMoves = {0, 0};
        int[] lastTurn = {GameLogic.BLACK};
        boolean[] resultReported = {false};

        int myColor = 0;
        if (robot != null) {
            boolean ub = robot.getAiPlayer() == GameLogic.WHITE;
            myColor = ub ? GameLogic.BLACK : GameLogic.WHITE;
            names[0] = ub ? userName : "AI";   names[1] = ub ? "AI" : userName;
            avatars[0] = ub ? avatarImage : null; avatars[1] = ub ? null : avatarImage;
        } else if (net != null) {
            myColor = localPlayer;
            boolean ub = localPlayer == GameLogic.BLACK;
            String op = opponentName != null && !opponentName.isEmpty() ? opponentName : "对手";
            names[0] = ub ? userName : op; names[1] = ub ? op : userName;
            avatars[0] = ub ? avatarImage : null; avatars[1] = ub ? null : avatarImage;
        } else {
            names[0] = "黑方"; names[1] = "白方";
        }
        int myIdx = myColor == GameLogic.BLACK ? 0 : myColor == GameLogic.WHITE ? 1 : -1;
        int myColorF = myColor;

        // ── 加载统计 ──
        JPanel blackCard = playerCard(names, avatars, 0, GameLogic.BLACK, game, countdown, totalTime, stats);
        JPanel whiteCard = playerCard(names, avatars, 1, GameLogic.WHITE, game, countdown, totalTime, stats);

        if (!currentUser.isGuest() && myIdx >= 0) {
            int mi = myIdx;
            new Thread(() -> {
                String r = OnlinePlay.get("stats&user_id=" + currentUser.id());
                if (r != null && r.contains("\"success\":true")) {
                    stats[mi][0] = OnlinePlay.extractInt(r, "total_games");
                    stats[mi][1] = OnlinePlay.extractInt(r, "wins");
                    stats[mi][2] = OnlinePlay.extractInt(r, "losses");
                    stats[mi][3] = OnlinePlay.extractInt(r, "win_streak");
                    stats[mi][4] = OnlinePlay.extractInt(r, "total_score");
                    SwingUtilities.invokeLater(() -> { blackCard.repaint(); whiteCard.repaint(); });
                }
            }, "LoadStats").start();
        }

        if (existingGame != null) {
            lastTurn[0] = game.getCurrentPlayer();
            if (game.isGameOver()) resultReported[0] = true;
        }

        // ── 计时器 ──
        JLabel totalLabel = new JLabel("", SwingConstants.CENTER);
        totalLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
        totalLabel.setForeground(new Color(160, 165, 175));
        Runnable updateTotal = () -> totalLabel.setText("<html><center><span style='font-size:9px;color:#888'>对局用时</span><br>" + fmtTime(totalTime[0] + totalTime[1]) + "</center></html>");
        updateTotal.run();

        long[] lastSec = {System.currentTimeMillis()};
        boolean[] autoMoving = {false};
        javax.swing.Timer clock = new javax.swing.Timer(200, e -> {
            if (game.isGameOver()) return;
            long now = System.currentTimeMillis();
            if (now - lastSec[0] >= 1000) {
                lastSec[0] = now;
                int ci = game.getCurrentPlayer() == GameLogic.BLACK ? 0 : 1;
                totalTime[ci]++;
                if (countdown[ci] > 0) countdown[ci]--;
                updateTotal.run();
                if (countdown[ci] <= 0 && !autoMoving[0]) {
                    autoMoving[0] = true;
                    autoMoves[ci]++;
                    int cur = game.getCurrentPlayer();
                    Robot tempAi = new Robot(cur == GameLogic.BLACK ? GameLogic.WHITE : GameLogic.BLACK);
                    int[] mv = tempAi.bestMove(game);
                    game.placePiece(mv[0], mv[1]);
                    boardPanel.repaint();
                    boardPanel.updateStatusPublic();
                    if (net != null) net.sendMove(mv[0], mv[1]);
                    autoMoving[0] = false;
                }
            }
            blackCard.repaint(); whiteCard.repaint();
        });
        clock.start();

        Runnable resetTimers = () -> {
            countdown[0] = countdown[1] = 30;
            totalTime[0] = totalTime[1] = 0;
            autoMoves[0] = autoMoves[1] = 0;
            lastTurn[0] = GameLogic.BLACK;
            resultReported[0] = false;
            if (!clock.isRunning()) clock.start();
            updateTotal.run();
        };

        // ── 状态回调 ──
        boardPanel.setOnChange(() -> {
            int cur = game.getCurrentPlayer();
            if (!game.isGameOver() && cur != lastTurn[0]) {
                countdown[cur == GameLogic.BLACK ? 0 : 1] = 30;
                lastTurn[0] = cur;
            }
            if (game.isGameOver() && !resultReported[0]) {
                resultReported[0] = true;
                clock.stop();
                if (myColorF != 0 && !currentUser.isGuest()) {
                    String result = game.getWinner() == 0 ? "draw" : game.getWinner() == myColorF ? "win" : "loss";
                    if (myIdx >= 0) {
                        stats[myIdx][0]++;
                        if ("win".equals(result)) { stats[myIdx][1]++; stats[myIdx][3]++; }
                        else if ("loss".equals(result)) { stats[myIdx][2]++; stats[myIdx][3] = 0; }
                    }

                    int base = "win".equals(result) ? 100 : "draw".equals(result) ? 50 : 0;
                    int myTime = myIdx >= 0 ? totalTime[myIdx] : 0;
                    int myAuto = myIdx >= 0 ? autoMoves[myIdx] : 0;
                    double timeMul = Math.max(0.5, 2.0 - myTime / 300.0);
                    double aiMul = Math.max(0.0, 1.0 - 0.05 * myAuto);
                    int score = (int) Math.round(base * timeMul * aiMul);
                    if (myIdx >= 0) stats[myIdx][4] += score;

                    int scoreF = score;
                    new Thread(() -> OnlinePlay.post("report_result",
                            "{\"user_id\":" + currentUser.id()
                            + ",\"username\":\"" + currentUser.username().replace("\"","\\\"") + "\""
                            + ",\"result\":\"" + result + "\""
                            + ",\"score\":" + scoreF + "}"), "Report").start();

                    if (base > 0) {
                        String dlgTitle = "win".equals(result) ? "胜利" : "平局";
                        String msg = "<html><table cellpadding='2'>"
                                + "<tr><td>基础积分</td><td align='right'>" + base + "</td></tr>"
                                + "<tr><td>用时倍率</td><td align='right'>×" + String.format("%.2f", timeMul) + "  (" + fmtTime(myTime) + ")</td></tr>"
                                + (myAuto > 0 ? "<tr><td>AI代下</td><td align='right' style='color:red'>-" + (myAuto * 5) + "%  (" + myAuto + "次)</td></tr>" : "")
                                + "<tr><td colspan='2'><hr></td></tr>"
                                + "<tr><td><b>获得积分</b></td><td align='right'><b>+" + score + "</b></td></tr>"
                                + "</table></html>";
                        JOptionPane.showMessageDialog(boardPanel.getTopLevelAncestor(), msg, dlgTitle, JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
            blackCard.repaint(); whiteCard.repaint();
        });

        // ── 右侧面板 ──
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(new Color(30, 34, 42));
        side.setPreferredSize(new Dimension(160, 0));
        side.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        side.add(blackCard, BorderLayout.NORTH);
        side.add(totalLabel, BorderLayout.CENTER);
        side.add(whiteCard, BorderLayout.SOUTH);

        // ── 网络回调 ──
        if (net != null) {
            int opIdx = localPlayer == GameLogic.BLACK ? 1 : 0;
            String myName = currentUser.isGuest() ? "游客" : currentUser.displayName();
            net.sendName(myName);
            net.setCallback(new NetworkPlay.Callback() {
                @Override public void onRemoteMove(int r, int c) { boardPanel.receiveRemoteMove(r, c); }
                @Override public void onRemoteReset() {
                    SwingUtilities.invokeLater(() -> { boardPanel.receiveRemoteReset(); resetTimers.run(); });
                }
                @Override public void onRemoteName(String n) {
                    SwingUtilities.invokeLater(() -> {
                        names[opIdx] = n;
                        boardPanel.setOpponentName(n);
                        blackCard.repaint(); whiteCard.repaint();
                    });
                }
                @Override public void onRemoteSurrender() {
                    SwingUtilities.invokeLater(() -> {
                        int opColor = localPlayer == GameLogic.BLACK ? GameLogic.WHITE : GameLogic.BLACK;
                        game.surrender(opColor);
                        clock.stop();
                        boardPanel.repaint();
                        boardPanel.updateStatusPublic();
                        JOptionPane.showMessageDialog(frame, "对方投降，你赢了！", "对局结束", JOptionPane.INFORMATION_MESSAGE);
                    });
                }
                @Override public void onDisconnect(String reason) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, reason, "连接断开", JOptionPane.WARNING_MESSAGE));
                }
            });
            net.startListening();
        }

        // ── 底栏 ──
        boolean isOnline = net != null;

        JButton actionBtn;
        if (isOnline) {
            actionBtn = new JButton("投降");
            actionBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            actionBtn.setForeground(new Color(200, 60, 60));
            actionBtn.setFocusPainted(false);
            actionBtn.addActionListener(e -> {
                if (game.isGameOver()) return;
                int confirm = JOptionPane.showConfirmDialog(frame, "确定要投降吗？", "投降", JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) return;
                game.surrender(localPlayer);
                clock.stop();
                net.sendSurrender();
                boardPanel.repaint();
                boardPanel.updateStatusPublic();
            });
        } else {
            actionBtn = new JButton("重新开始");
            actionBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            actionBtn.setFocusPainted(false);
            actionBtn.addActionListener(e -> { boardPanel.resetGame(); resetTimers.run(); });
        }

        JButton backBtn = new JButton("返回大厅");
        backBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        backBtn.setFocusPainted(false);
        backBtn.addActionListener(e -> {
            if (net instanceof OnlinePlay op && !game.isGameOver()) op.disconnect();
            else if (net != null) net.close();
            frame.dispose();
        });

        JLabel modeLabel = new JLabel(modeText, SwingConstants.CENTER);
        modeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        modeLabel.setForeground(new Color(100, 100, 100));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.add(backBtn);
        btnPanel.add(actionBtn);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(modeLabel, BorderLayout.NORTH);
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(btnPanel, BorderLayout.EAST);

        frame.setLayout(new BorderLayout());
        frame.add(boardPanel, BorderLayout.CENTER);
        frame.add(side, BorderLayout.EAST);
        frame.add(bottom, BorderLayout.SOUTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                clock.stop();
                if (net instanceof OnlinePlay op && !game.isGameOver()) op.disconnect();
                else if (net != null) net.close();
                setVisible(true);
                checkActiveRoom();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        if (robot != null && robot.getAiPlayer() == GameLogic.BLACK) boardPanel.triggerFirstAiMove();
    }

    private JPanel playerCard(String[] names, Image[] avatars, int idx, int pieceColor,
                              GameLogic game, int[] countdown, int[] totalTime, int[][] stats) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                boolean active = !game.isGameOver() && game.getCurrentPlayer() == pieceColor;
                boolean winner = game.isGameOver() && game.getWinner() == pieceColor;
                int ci = pieceColor == GameLogic.BLACK ? 0 : 1;

                if (active || winner) {
                    g2.setColor(winner ? new Color(180, 140, 40, 50) : new Color(60, 140, 60, 50));
                    g2.fillRoundRect(2, 2, w - 4, getHeight() - 4, 10, 10);
                    g2.setColor(winner ? new Color(210, 170, 50, 120) : new Color(80, 180, 80, 100));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(2, 2, w - 4, getHeight() - 4, 10, 10);
                }

                // 颜色标签
                String label = pieceColor == GameLogic.BLACK ? "● 黑方" : "○ 白方";
                g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
                g2.setColor(new Color(160, 160, 160));
                FontMetrics fm0 = g2.getFontMetrics();
                g2.drawString(label, (w - fm0.stringWidth(label)) / 2, 18);

                // 头像
                int avSize = 40;
                int avX = (w - avSize) / 2, avY = 26;
                Shape circle = new Ellipse2D.Float(avX, avY, avSize, avSize);
                Image av = avatars[idx];
                String name = names[idx] != null ? names[idx] : "?";

                if (av != null) {
                    Shape old = g2.getClip();
                    g2.setClip(circle);
                    g2.drawImage(av, avX, avY, avSize, avSize, null);
                    g2.setClip(old);
                    g2.setColor(new Color(60, 65, 80));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(circle);
                } else {
                    g2.setColor(new Color(70, 75, 95));
                    g2.fill(circle);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
                    String ch = name.substring(0, 1).toUpperCase();
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(ch, avX + (avSize - fm.stringWidth(ch)) / 2,
                            avY + (avSize - fm.getHeight()) / 2 + fm.getAscent());
                }

                // 名字
                int nameY = avY + avSize + 16;
                g2.setColor(new Color(220, 220, 220));
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
                FontMetrics fm2 = g2.getFontMetrics();
                String dn = name.length() > 8 ? name.substring(0, 7) + "…" : name;
                g2.drawString(dn, (w - fm2.stringWidth(dn)) / 2, nameY);

                // 倒计时
                int cd = countdown[ci];
                boolean urgent = cd <= 10 && active;
                boolean blink = urgent && (System.currentTimeMillis() / 200) % 2 == 0;

                if (urgent) {
                    int alpha = blink ? 40 : 20;
                    g2.setColor(new Color(255, 30, 30, alpha));
                    g2.fillRoundRect(4, nameY + 4, w - 8, 28, 8, 8);
                }

                int cdY = nameY + 24;
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
                FontMetrics fmCd = g2.getFontMetrics();
                String cdT = fmtTime(cd);
                if (urgent) {
                    g2.setColor(blink ? new Color(255, 50, 50) : new Color(200, 60, 60));
                } else if (active) {
                    g2.setColor(new Color(220, 220, 220));
                } else {
                    g2.setColor(new Color(120, 120, 120));
                }
                g2.drawString(cdT, (w - fmCd.stringWidth(cdT)) / 2, cdY);

                // 总用时
                int ttY = cdY + 16;
                g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
                g2.setColor(new Color(110, 110, 110));
                FontMetrics fm3 = g2.getFontMetrics();
                String ttT = "用时 " + fmtTime(totalTime[ci]);
                g2.drawString(ttT, (w - fm3.stringWidth(ttT)) / 2, ttY);

                // 统计
                int stTotal = stats[idx][0], stW = stats[idx][1], stL = stats[idx][2], stS = stats[idx][3], stSc = stats[idx][4];
                if (stTotal > 0 || stSc > 0) {
                    g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
                    FontMetrics fm4 = g2.getFontMetrics();
                    int sY = ttY + 16;
                    if (stTotal > 0) {
                        g2.setColor(new Color(140, 140, 140));
                        String sl = stW + "胜 " + stL + "负 共" + stTotal + "场";
                        g2.drawString(sl, (w - fm4.stringWidth(sl)) / 2, sY);
                        sY += 14;
                    }
                    if (stSc > 0) {
                        g2.setColor(new Color(100, 200, 255));
                        String sc = "★ " + stSc + " 分";
                        g2.drawString(sc, (w - fm4.stringWidth(sc)) / 2, sY);
                        sY += 14;
                    }
                    if (stS > 0) {
                        g2.setColor(new Color(255, 180, 50));
                        String sk = "🔥 连胜 " + stS;
                        g2.drawString(sk, (w - fm4.stringWidth(sk)) / 2, sY);
                    }
                }

                // 思考中
                if (active) {
                    g2.setColor(new Color(100, 200, 100));
                    g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
                    FontMetrics fm5 = g2.getFontMetrics();
                    String tip = "思考中...";
                    g2.drawString(tip, (w - fm5.stringWidth(tip)) / 2, getHeight() - 8);
                }
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(144, 210));
        return card;
    }

    private static String fmtTime(int sec) {
        if (sec < 0) sec = 0;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
}
