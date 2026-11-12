package com.mushroom.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mushroom.common.ApiException;
import com.mushroom.common.Db;
import com.mushroom.security.AuthUser;
import com.mushroom.security.CurrentUser;
import com.mushroom.service.NotificationService;

/**
 * Yêu cầu nhận nấm tại trang trại (pickup) hoặc giao tận nơi (delivery).
 * Trạng thái: pending -> confirmed -> delivering -> done | cancelled
 */
@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private static final Map<String, String> LABEL = Map.of(
            "pending", "Chờ xác nhận", "confirmed", "Đã xác nhận", "delivering", "Đang giao",
            "done", "Hoàn tất", "cancelled", "Đã hủy");

    private final Db db;
    private final NotificationService notifications;

    public DeliveryController(Db db, NotificationService notifications) {
        this.db = db;
        this.notifications = notifications;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        AuthUser u = CurrentUser.get();
        String sql = """
                SELECT d.*, u.full_name AS customer_name, u.phone AS customer_phone, t.code AS tray_code
                FROM delivery_requests d JOIN users u ON u.id = d.customer_id LEFT JOIN trays t ON t.id = d.tray_id""";
        return u.isCustomer()
                ? db.list(sql + " WHERE d.customer_id = ? ORDER BY d.created_at DESC", u.id())
                : db.list(sql + " ORDER BY (d.status IN ('pending','confirmed','delivering')) DESC, d.created_at DESC");
    }

    /** Khách: { tray_id, type: "pickup"|"delivery", address, note } */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> b) {
        AuthUser u = CurrentUser.require("customer");
        String type = Db.str(b.get("type"));
        String address = Db.str(b.get("address"));
        Long trayId = Db.toLong(b.get("tray_id"));
        if (!"pickup".equals(type) && !"delivery".equals(type)) throw ApiException.badRequest("type phải là pickup hoặc delivery");
        if ("delivery".equals(type) && address == null) throw ApiException.badRequest("Giao tận nơi cần có địa chỉ");
        if (trayId != null && db.count("SELECT count(*) FROM rentals WHERE tray_id = ? AND customer_id = ? AND status = 'active'", trayId, u.id()) == 0) {
            throw ApiException.forbidden("Bạn không thuê khay này");
        }
        Map<String, Object> d = db.one("""
                INSERT INTO delivery_requests (customer_id, tray_id, type, address, note) VALUES (?, ?, ?, ?, ?) RETURNING *""",
                u.id(), trayId, type, address, Db.str(b.get("note")));
        notifications.notifyStaff("delivery", "Yêu cầu nhận/giao nấm mới",
                ("pickup".equals(type) ? "Nhận tại trang trại" : "Giao tận nơi") + " - khách #" + u.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(d);
    }

    /** Nhân viên cập nhật trạng thái; khách chỉ được hủy yêu cầu đang chờ */
    @PutMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        AuthUser u = CurrentUser.get();
        String status = Db.str(b.get("status"));
        if (status == null || !LABEL.containsKey(status)) throw ApiException.badRequest("status không hợp lệ");
        Map<String, Object> d;
        if (u.isCustomer()) {
            if (!"cancelled".equals(status)) throw ApiException.forbidden("Khách hàng chỉ được hủy yêu cầu");
            d = db.one("UPDATE delivery_requests SET status = 'cancelled' WHERE id = ? AND customer_id = ? AND status = 'pending' RETURNING *", id, u.id());
        } else {
            d = db.one("UPDATE delivery_requests SET status = ? WHERE id = ? RETURNING *", status, id);
            if (d != null) notifications.notifyCustomers("delivery", List.of(Db.toLong(d.get("customer_id"))),
                    "Cập nhật yêu cầu nhận/giao nấm", "Trạng thái: " + LABEL.get(status));
        }
        if (d == null) throw ApiException.notFound("Không tìm thấy yêu cầu");
        return d;
    }
}
