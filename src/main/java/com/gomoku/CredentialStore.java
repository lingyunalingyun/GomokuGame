package com.gomoku;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * 凭据持久化存储类（功能一：用户登录的记住密码功能）
 * 将用户名和 Base64 编码的密码保存到 ~/.gomoku/credentials 文件
 * 使用 Java NIO Files API 进行文件读写操作
 */
public class CredentialStore {

    private static final Path FILE = Path.of(System.getProperty("user.home"), ".gomoku", "credentials");

    public static void save(String username, String password) {
        try {
            Files.createDirectories(FILE.getParent());
            String encoded = Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
            Files.writeString(FILE, username + "\n" + encoded);
        } catch (IOException ignored) {}
    }

    public static String[] load() {
        try {
            if (!Files.exists(FILE)) return null;
            List<String> lines = Files.readAllLines(FILE);
            if (lines.size() < 2) return null;
            String password = new String(Base64.getDecoder().decode(lines.get(1)), StandardCharsets.UTF_8);
            return new String[]{lines.get(0), password};
        } catch (Exception e) { return null; }
    }

    public static void clear() {
        try { Files.deleteIfExists(FILE); } catch (IOException ignored) {}
    }
}
