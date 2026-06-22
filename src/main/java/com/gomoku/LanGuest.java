package com.gomoku;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class LanGuest implements NetworkPlay {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Callback callback;

    public record RoomInfo(String name, String ip, int port, String hostName) {
        @Override
        public String toString() { return name + "  (" + hostName + " @ " + ip + ")"; }
    }

    public static DatagramSocket startDiscovery(Consumer<RoomInfo> onFound) {
        try {
            DatagramSocket ds = new DatagramSocket(null);
            ds.setReuseAddress(true);
            ds.bind(new InetSocketAddress(LanHost.UDP_PORT));
            ds.setSoTimeout(2000);
            new Thread(() -> {
                byte[] buf = new byte[512];
                while (!ds.isClosed()) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    try {
                        ds.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                        String[] parts = msg.split("\\|");
                        if (parts.length >= 3 && LanHost.MAGIC.equals(parts[0])) {
                            String ip = pkt.getAddress().getHostAddress();
                            int port = Integer.parseInt(parts[2]);
                            String hostName = parts.length >= 4 ? parts[3] : "未知";
                            onFound.accept(new RoomInfo(parts[1], ip, port, hostName));
                        }
                    } catch (SocketTimeoutException ignored) {
                    } catch (SocketException e) {
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, "Discovery").start();
            return ds;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void connect(RoomInfo room) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(room.ip(), room.port()), 5000);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
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
        }, "GuestListen").start();
    }

    @Override
    public void sendMove(int row, int col) { if (out != null) out.println("MOVE " + row + " " + col); }

    @Override
    public void sendReset() { if (out != null) out.println("RESET"); }

    @Override
    public void sendSurrender() { if (out != null) out.println("SURRENDER"); }

    @Override
    public void close() {
        try { if (out != null) out.println("QUIT"); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }
}
