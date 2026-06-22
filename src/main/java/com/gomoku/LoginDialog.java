package com.gomoku;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

public class LoginDialog {

    public static UserInfo show(Component owner) {
        AtomicReference<UserInfo> result = new AtomicReference<>();

        Frame frame = owner instanceof Frame f ? f : null;
        JDialog dialog = new JDialog(frame, "五子棋 · 登录", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 15, 30));

        JCheckBox museCheck = new JCheckBox("缪斯树屋论坛账号  musetreehouse.com");
        museCheck.setSelected(true);
        museCheck.setEnabled(false);
        museCheck.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        museCheck.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, 8);

        JLabel userLabel = new JLabel("用户名");
        userLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        JTextField usernameField = new JTextField(18);
        usernameField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        JLabel passLabel = new JLabel("密  码");
        passLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        JPasswordField passwordField = new JPasswordField(18);
        passwordField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(userLabel, gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(usernameField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(passLabel, gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(passwordField, gbc);

        JCheckBox rememberCheck = new JCheckBox("记住密码");
        rememberCheck.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        rememberCheck.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] saved = CredentialStore.load();
        if (saved != null) {
            usernameField.setText(saved[0]);
            passwordField.setText(saved[1]);
            rememberCheck.setSelected(true);
        }

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(200, 50, 50));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton loginBtn = new JButton("登 录");
        loginBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        JButton guestBtn = new JButton("游客进入");
        guestBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnPanel.add(loginBtn);
        btnPanel.add(guestBtn);

        panel.add(museCheck);
        panel.add(Box.createVerticalStrut(12));
        panel.add(formPanel);
        panel.add(Box.createVerticalStrut(2));
        panel.add(rememberCheck);
        panel.add(Box.createVerticalStrut(4));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(btnPanel);

        loginBtn.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            if (username.isEmpty() || password.isEmpty()) {
                statusLabel.setText("用户名和密码不能为空");
                return;
            }
            statusLabel.setText("登录中...");
            statusLabel.setForeground(new Color(100, 100, 100));
            loginBtn.setEnabled(false);
            guestBtn.setEnabled(false);

            new Thread(() -> {
                try {
                    UserInfo info = AuthService.login(username, password);
                    if (rememberCheck.isSelected()) {
                        CredentialStore.save(username, password);
                    } else {
                        CredentialStore.clear();
                    }
                    SwingUtilities.invokeLater(() -> {
                        result.set(info);
                        dialog.dispose();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setForeground(new Color(200, 50, 50));
                        statusLabel.setText(ex.getMessage());
                        loginBtn.setEnabled(true);
                        guestBtn.setEnabled(true);
                    });
                }
            }, "Login").start();
        });

        guestBtn.addActionListener(e -> {
            result.set(UserInfo.GUEST);
            dialog.dispose();
        });

        passwordField.addActionListener(e -> loginBtn.doClick());
        dialog.getRootPane().setDefaultButton(loginBtn);

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);

        return result.get();
    }
}
