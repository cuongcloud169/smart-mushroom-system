package com.mushroom.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mushroom.common.ApiException;
import com.mushroom.common.Db;
import com.mushroom.security.CurrentUser;

/** Thông báo của người dùng hiện tại + cài đặt thông báo (Admin). */
@RestController
@RequestMapping("/api")
public class NotificationController {
    private final Db db;

    public NotificationController(Db db) {
        this.db = db;
    }

    @GetMapping("/notifications")
    public List<Map<String, Object>> list() {
        return db.list("SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 100", CurrentUser.get().id());
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Object> unread() {
        return Map.of("count", db.count("SELECT count(*) FROM notifications WHERE user_id = ? AND NOT is_read", CurrentUser.get().id()));
    }

    @PutMapping("/notifications/read-all")
    public Map<String, Object> readAll() {
        db.update("UPDATE notifications SET is_read = TRUE WHERE user_id = ?", CurrentUser.get().id());
        return Map.of("ok", true);
    }

    @PutMapping("/notifications/{id}/read")
    public Map<String, Object> read(@PathVariable Long id) {
        db.update("UPDATE notifications SET is_read = TRUE WHERE id = ? AND user_id = ?", id, CurrentUser.get().id());
        return Map.of("ok", true);
    }

    // ----- Cài đặt thông báo -----

    @GetMapping("/notification-settings")
    public List<Map<String, Object>> settings() {
        CurrentUser.requireStaff();
        return db.list("SELECT * FROM notification_settings ORDER BY event_type");
    }

    /** Admin bật/tắt một loại thông báo: { enabled, notify_staff, notify_customer } */
    @PutMapping("/notification-settings/{eventType}")
    public Map<String, Object> updateSetting(@PathVariable String eventType, @RequestBody Map<String, Object> b) {
        CurrentUser.requireAdmin();
        Map<String, Object> s = db.one("""
                UPDATE notification_settings SET enabled = COALESCE(?::boolean, enabled),
                  notify_staff = COALESCE(?::boolean, notify_staff), notify_customer = COALESCE(?::boolean, notify_customer)
                WHERE event_type = ? RETURNING *""",
                bool(b.get("enabled")), bool(b.get("notify_staff")), bool(b.get("notify_customer")), eventType);
        if (s == null) throw ApiException.notFound("Không tìm thấy loại thông báo");
        return s;
    }

    private static Boolean bool(Object o) {
        return o == null ? null : (o instanceof Boolean b ? b : Boolean.valueOf(o.toString()));
    }
}
