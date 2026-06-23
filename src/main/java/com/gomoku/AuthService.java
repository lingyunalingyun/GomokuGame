package com.gomoku;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 认证服务类（功能一：用户登录的网络通信部分）
 * 使用 Java 11+ HttpClient 向服务器发送 JSON 登录请求
 * 解析 JSON 响应构造 UserInfo 对象
 */
public class AuthService {

    private static final String LOGIN_URL = "https://musetreehouse.com/api/game_login.php";

    public static UserInfo login(String username, String password) throws Exception {
        String json = "{\"username\":\"" + escapeJson(username)
                + "\",\"password\":\"" + escapeJson(password) + "\"}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOGIN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();

        if (body.contains("\"success\":false")) {
            String error = extractString(body, "error");
            throw new Exception(error != null ? error : "登录失败");
        }
        if (!body.contains("\"success\":true")) {
            throw new Exception("服务器响应异常 (HTTP " + response.statusCode() + ")");
        }

        return new UserInfo(
                extractInt(body, "id"),
                extractString(body, "mid"),
                extractString(body, "username"),
                extractString(body, "role"),
                extractInt(body, "level"),
                extractString(body, "avatar")
        );
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractString(String json, String key) {
        String prefix = "\"" + key + "\":\"";
        int start = json.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                if (next == 'u' && i + 4 < json.length()) {
                    sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                    i += 4;
                } else {
                    sb.append(next);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int extractInt(String json, String key) {
        String prefix = "\"" + key + "\":";
        int start = json.indexOf(prefix);
        if (start < 0) return 0;
        start += prefix.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == start) return 0;
        return Integer.parseInt(json.substring(start, end));
    }
}
