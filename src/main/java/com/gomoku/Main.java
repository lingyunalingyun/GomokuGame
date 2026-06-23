package com.gomoku;

import javax.swing.*;

/**
 * 程序入口类
 * 通过 SwingUtilities.invokeLater 在 EDT（事件分发线程）中启动主窗口，保证线程安全
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LobbyFrame().setVisible(true));
    }
}
