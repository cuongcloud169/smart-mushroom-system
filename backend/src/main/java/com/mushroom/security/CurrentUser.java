package com.mushroom.security;

import java.util.Arrays;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mushroom.common.ApiException;

/**
 * Lấy người dùng hiện tại và kiểm tra quyền.
 * Server luôn tự kiểm tra quyền ở mọi API, không tin vào giao diện.
 */
public final class CurrentUser {
    private CurrentUser() {}

    public static AuthUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AuthUser u)) {
            throw ApiException.unauthorized("Thiếu token đăng nhập");
        }
        return u;
    }

    /** Ném 403 nếu vai trò hiện tại không nằm trong danh sách. */
    public static AuthUser require(String... roles) {
        AuthUser u = get();
        if (!Arrays.asList(roles).contains(u.role())) {
            throw ApiException.forbidden("Không đủ quyền truy cập");
        }
        return u;
    }

    public static AuthUser requireStaff() {
        return require("admin", "operator");
    }

    public static AuthUser requireAdmin() {
        return require("admin");
    }
}
