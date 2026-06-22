package com.gomoku;

public interface NetworkPlay {

    void sendMove(int row, int col);

    void sendReset();

    void sendName(String name);

    void setCallback(Callback callback);

    void startListening();

    void close();

    void sendSurrender();

    interface Callback {
        void onRemoteMove(int row, int col);
        void onRemoteReset();
        void onRemoteName(String name);
        void onRemoteSurrender();
        void onDisconnect(String reason);
    }
}
