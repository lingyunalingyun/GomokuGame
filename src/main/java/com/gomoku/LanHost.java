package com.gomoku;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * 局域网主机端（功能二：在线对战的局域网模式）
 * 实现 NetworkPlay 接口
 * 使用 UDP 广播房间信息供客户端发现，TCP ServerSocket 等待客户端连接
 * 通过 TCP 流（PrintWriter/BufferedReader）收发文本协议命令
 */
public class LanHost implements NetworkPlay {

    static final int UDP_PORT = 23456;
    static final String MAGIC = "GOMOKU_ROOM";

    private final ServerSocket serverSocket;
    private final String roomName;
    private final String hostDisplayName;
    private Socket client;
    private PrintWriter out;
    private BufferedReader in;
    private DatagramSocket udpSocket;
    private volatile boolean broadcasting = true;
    private Callback callback;

    public LanHost(String roomName, String hostDisplayName) throws IOException {
        this.roomName = roomName;
        this.hostDisplayName = hostDisplayName;
        this.serverSocket = new ServerSocket(0);
    }

    public int getPort() { return serverSocket.getLocalPort(); }

    public String getLocalIp() {
        try {
            var ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                var ni = ifaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                var addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    var a = addrs.nextElement();
                    if (a instanceof Inet4Address) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public void startBroadcast() {
        new Thread(() -> {
            try {
                udpSocket = new DatagramSocket();
                udpSocket.setBroadcast(true);
                byte[] data = (MAGIC + "|" + roomName + "|" + serverSocket.getLocalPort()
                        + "|" + hostDisplayName).getBytes(StandardCharsets.UTF_8);
                InetAddress broadcast = InetAddress.getByName("255.255.255.255");
                while (broadcasting) {
                    udpSocket.send(new DatagramPacket(data, data.length, broadcast, UDP_PORT));
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                if (broadcasting) e.printStackTrace();
            }
        }, "Broadcast").start();
    }

    public void waitForClient() throws IOException {
        client = serverSocket.accept();
        out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
        in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        stopBroadcast();
    }

    public void cancelWait() {
        stopBroadcast();
        try { serverSocket.close(); } catch (Exception ignored) {}
    }

    private void stopBroadcast() {
        broadcasting = false;
        if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
    }

    @Override
    public void setCallback(Callback cb) { this.callback = cb; }

    @Override
    public void sendName(String name) { if (out != null) out.println("NAME " + name); }

    @Override
    public void startListening() {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("MOVE ")) {
                        String[] p = line.split(" ");
                        if (callback != null) callback.onRemoteMove(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    } else if (line.startsWith("NAME ")) {
                        if (callback != null) callback.onRemoteName(line.substring(5));
                    } else if ("RESET".equals(line)) {
                        if (callback != null) callback.onRemoteReset();
                    } else if ("SURRENDER".equals(line)) {
                        if (callback != null) callback.onRemoteSurrender();
                    } else if ("QUIT".equals(line)) {
                        if (callback != null) callback.onDisconnect("对手已退出");
                        break;
                    }
                }
            } catch (IOException e) {
                if (callback != null) callback.onDisconnect("连接断开");
            }
        }, "HostListen").start();
    }

    @Override
    public void sendMove(int row, int col) { if (out != null) out.println("MOVE " + row + " " + col); }

    @Override
    public void sendReset() { if (out != null) out.println("RESET"); }

    @Override
    public void sendSurrender() { if (out != null) out.println("SURRENDER"); }

    @Override
    public void close() {
        stopBroadcast();
        try { if (out != null) out.println("QUIT"); } catch (Exception ignored) {}
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        try { serverSocket.close(); } catch (Exception ignored) {}
    }
}
