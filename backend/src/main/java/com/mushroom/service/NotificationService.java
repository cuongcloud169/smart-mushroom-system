package com.mushroom.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.mushroom.common.Db;

/**
 * Gửi thông báo trong hệ thống (lưu bảng notifications, web/app đọc về).
 * Mọi thông báo đi qua notifyEvent() để tôn trọng cài đặt thông báo của Admin
 * (bảng notification_settings: bật/tắt từng loại, gửi cho nhân viên và/hoặc khách hàng).
 */
@Service
public class NotificationService {
    private final Db db;

    public NotificationService(Db db) {
        this.db = db;
    }

    /**
     * @param eventType   env_alert | device_offline | progress | harvest | rental | rental_expiry | delivery
     * @param customerIds khách hàng liên quan (có thể rỗng)
     */
    public void notifyEvent(String eventType, Collection<Long> customerIds, String title, String body) {
        send(eventType, true, customerIds, title, body);
    }

    /** Chỉ gửi cho nhân viên (Admin, Operator) */
    public void notifyStaff(String eventType, String title, String body) {
        send(eventType, true, List.of(), title, body);
    }

    /** Chỉ gửi cho các khách hàng được chỉ định */
    public void notifyCustomers(String eventType, Collection<Long> customerIds, String title, String body) {
        send(eventType, false, customerIds, title, body);
    }

    private void send(String eventType, boolean includeStaff, Collection<Long> customerIds, String title, String body) {
        Map<String, Object> s = db.one("SELECT * FROM notification_settings WHERE event_type = ?", eventType);
        if (s != null && !Boolean.TRUE.equals(s.get("enabled"))) return;
        boolean toStaff = includeStaff && (s == null || Boolean.TRUE.equals(s.get("notify_staff")));
        boolean toCustomer = s == null || Boolean.TRUE.equals(s.get("notify_customer"));

        Set<Long> ids = new LinkedHashSet<>();
        if (toStaff) ids.addAll(staffIds());
        if (toCustomer && customerIds != null) customerIds.stream().filter(Objects::nonNull).forEach(ids::add);
        for (Long id : ids) {
            db.update("INSERT INTO notifications (user_id, title, body, type) VALUES (?, ?, ?, ?)", id, title, body, eventType);
        }
    }

    public List<Long> staffIds() {
        return db.ids("SELECT id FROM users WHERE role IN ('admin','operator') AND is_active");
    }

    /** Khách đang thuê các khay trong một phòng */
    public List<Long> roomCustomerIds(Long roomId) {
        return db.ids("""
                SELECT DISTINCT r.customer_id FROM rentals r JOIN trays t ON t.id = r.tray_id
                WHERE t.room_id = ? AND r.status = 'active' AND r.customer_id IS NOT NULL""", roomId);
    }

    /** Khách đang thuê một khay (hoặc null) */
    public Long trayCustomerId(Long trayId) {
        List<Long> ids = db.ids("SELECT customer_id FROM rentals WHERE tray_id = ? AND status = 'active' AND customer_id IS NOT NULL", trayId);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
