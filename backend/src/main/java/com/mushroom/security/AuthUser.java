package com.mushroom.security;

/** Người dùng đang đăng nhập (lấy từ JWT). role: admin | operator | customer */
public record AuthUser(Long id, String role) {
    public boolean isCustomer() {
        return "customer".equals(role);
    }

    public boolean isStaff() {
        return "admin".equals(role) || "operator".equals(role);
    }
}
