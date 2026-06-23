package com.gomoku;

/**
 * 网络对战接口（功能二：在线对战的抽象层）
 * 定义了落子、重置、投降等网络通信方法
 * 通过 Callback 回调接口接收对手操作
 * 实现类：OnlinePlay（HTTP 在线）、LanHost/LanGuest（TCP 局域网）
 * 体现面向对象的多态特性——不同网络实现统一接口
 */
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
