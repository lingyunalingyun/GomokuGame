package com.gomoku;

/**
 * 用户信息数据类（Java 16+ record 不可变记录类型）
 * 封装用户 ID、论坛 MID、用户名、角色、等级、头像文件名
 * createGuest() 工厂方法生成带随机后缀的游客身份
 */
public record UserInfo(int id, String mid, String username, String role, int level, String avatar) {

    public static UserInfo createGuest() {
        StringBuilder sb = new StringBuilder("游客");
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            int c = rnd.nextInt(52);
            sb.append((char)(c < 26 ? 'A' + c : 'a' + c - 26));
        }
        return new UserInfo(0, "", sb.toString(), "guest", 0, "");
    }

    public boolean isGuest() { return id == 0; }

    public String displayName() {
        if (isGuest()) return username;
        return username + " (Lv." + level + ")";
    }
}
