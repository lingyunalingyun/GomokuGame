package com.gomoku;

public record UserInfo(int id, String mid, String username, String role, int level, String avatar) {

    public static final UserInfo GUEST = new UserInfo(0, "", "游客", "guest", 0, "");

    public boolean isGuest() { return id == 0; }

    public String displayName() {
        if (isGuest()) return "游客";
        return username + " (Lv." + level + ")";
    }
}
