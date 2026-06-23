package com.gomoku;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 在线对战实现类（功能二：在线对战/房间管理的核心）
 * 实现 NetworkPlay 接口，通过 HTTP 轮询与 PHP 服务端通信
 * 每 500ms 轮询一次服务器获取对手操作事件
 * 支持断线重连：replay() 方法回放历史棋步恢复棋局状态
 * C/S 架构：Java 客户端 + PHP+MySQL 服务端
 */
public class OnlinePlay implements NetworkPlay {

    private static final String API = "https://musetreehouse.com/api/game_room.php";
    private static final HttpClient HTTP = buildClient();

    private static HttpClient buildClient() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).sslContext(ctx).build();
        } catch (Exception e) {
            return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        }
    }

    private final int roomId;
    private final int myPlayer;
    private Callback callback;
    private volatile boolean running;
    private int lastEventId;

    public OnlinePlay(int roomId, int myPlayer) {
        this.roomId = roomId;
        this.myPlayer = myPlayer;
    }

    public int getRoomId() { return roomId; }
    public int getMyPlayer() { return myPlayer; }

    public void sendRematch() {
        post("rematch", "{\"room_id\":" + roomId + ",\"player\":" + myPlayer + "}");
    }

    // ── NetworkPlay 接口 ──

    @Override
    public void sendMove(int row, int col) {
        post("move", "{\"room_id\":" + roomId + ",\"player\":" + myPlayer + ",\"row\":" + row + ",\"col\":" + col + "}");
    }

    @Override
    public void sendReset() {
        post("reset", "{\"room_id\":" + roomId + ",\"player\":" + myPlayer + "}");
    }

    @Override
    public void sendSurrender() {
        post("surrender", "{\"room_id\":" + roomId + ",\"player\":" + myPlayer + "}");
    }

    @Override
    public void sendName(String name) {}

    @Override
    public void setCallback(Callback cb) { this.callback = cb; }

    @Override
    public void startListening() {
        running = true;
        new Thread(() -> {
            while (running) {
                try { Thread.sleep(500); poll(); }
                catch (InterruptedException e) { break; }
                catch (Exception ignored) {}
            }
        }, "OnlinePoll").start();
    }

    public void disconnect() { running = false; }

    @Override
    public void close() {
        if (!running) return;
        running = false;
        post("quit", "{\"room_id\":" + roomId + ",\"player\":" + myPlayer + "}");
    }

    public String replay(GameLogic game) {
        String body = get("poll&room_id=" + roomId + "&after=0");
        if (body == null || !body.contains("\"success\":true")) return null;
        String opName = null;
        int pos = body.indexOf("\"events\":[");
        if (pos < 0) return null;
        pos += 10;
        while (pos < body.length()) {
            int s = body.indexOf('{', pos);
            if (s < 0) break;
            int e = body.indexOf('}', s);
            if (e < 0) break;
            String obj = body.substring(s, e + 1);
            int id = extractInt(obj, "id");
            if (id > lastEventId) lastEventId = id;
            String type = extractStr(obj, "type");
            String data = extractStr(obj, "data");
            int player = extractInt(obj, "player");
            switch (type) {
                case "MOVE" -> { String[] p = data.split(","); if (p.length == 2) game.placePiece(Integer.parseInt(p[0]), Integer.parseInt(p[1])); }
                case "NAME" -> { if (player != myPlayer) opName = data; }
                case "SURRENDER" -> game.surrender(player == 1 ? GameLogic.BLACK : GameLogic.WHITE);
                case "RESET" -> game.reset();
            }
            pos = e + 1;
        }
        return opName;
    }

    // ── 轮询 ──

    private void poll() throws Exception {
        String body = get("poll&room_id=" + roomId + "&after=" + lastEventId);
        if (body == null || !body.contains("\"success\":true")) return;

        int pos = body.indexOf("\"events\":[");
        if (pos < 0) return;
        pos += 10;
        while (pos < body.length()) {
            int s = body.indexOf('{', pos);
            if (s < 0) break;
            int e = body.indexOf('}', s);
            if (e < 0) break;
            processEvent(body.substring(s, e + 1));
            pos = e + 1;
        }
    }

    private void processEvent(String obj) {
        int id = extractInt(obj, "id");
        if (id <= lastEventId) return;
        lastEventId = id;

        int player = extractInt(obj, "player");
        if (player == myPlayer) return;
        if (callback == null) return;

        String type = extractStr(obj, "type");
        String data = extractStr(obj, "data");

        switch (type) {
            case "MOVE" -> {
                String[] p = data.split(",");
                if (p.length == 2) callback.onRemoteMove(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            }
            case "RESET" -> callback.onRemoteReset();
            case "SURRENDER" -> callback.onRemoteSurrender();
            case "QUIT"  -> { running = false; callback.onDisconnect(data != null && !data.isEmpty() ? data : "对方已离开"); }
            case "NAME"  -> callback.onRemoteName(data);
            case "REMATCH" -> callback.onRematch();
            case "LEAVE" -> { running = false; callback.onOpponentLeft(); }
        }
    }

    // ── 静态 API 调用（供 LobbyFrame 用） ──

    public static String get(String actionParams) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API + "?action=" + actionParams))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) { return null; }
    }

    public static String post(String action, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API + "?action=" + action))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) { return null; }
    }

    static String extractStr(String json, String key) {
        String prefix = "\"" + key + "\":\"";
        int s = json.indexOf(prefix);
        if (s < 0) return "";
        s += prefix.length();
        int e = json.indexOf('"', s);
        return e < 0 ? "" : json.substring(s, e);
    }

    static int extractInt(String json, String key) {
        String prefix = "\"" + key + "\":";
        int s = json.indexOf(prefix);
        if (s < 0) return 0;
        s += prefix.length();
        int e = s;
        while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-')) e++;
        return e == s ? 0 : Integer.parseInt(json.substring(s, e));
    }
}
