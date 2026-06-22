package com.gomoku;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LobbyFrame().setVisible(true));
    }
}
