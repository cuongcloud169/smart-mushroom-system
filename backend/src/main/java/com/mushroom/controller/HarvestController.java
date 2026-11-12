package com.mushroom.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** Lịch thu hoạch và nhật ký thu hoạch (sản lượng kg). */
@RestController
@RequestMapping("/api/harvests")
public class HarvestController {
    private static final String SELECT = """
            SELECT h.*, t.code AS tray_code, rm.name AS room_name FROM harvest_schedules h
            JOIN trays t ON t.id = h.tray_id JOIN rooms rm ON rm.id = t.room_id""";

    private final Db db;
    private final NotificationService notifications;

    public HarvestController(Db db, NotificationService notifications) {
        this.db = db;
        this.notifications = notifications;
    }

    /** Nhân viên: tất cả lịch; khách: lịch của khay mình đang thuê */
    @GetMapping
    public List<Map<String, Object>> list() {
        AuthUser u = CurrentUser.get();
        return u.isCustomer()
                ? db.list(SELECT + " JOIN rentals rt ON rt.tray_id = t.id AND rt.status = 'active' AND rt.customer_id = ? ORDER BY h.planned_date", u.id())
                : db.list(SELECT + " ORDER BY (h.status = 'planned') DESC, h.planned_date");
    }

    /** { tray_id, planned_date: "2026-12-01", note } */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> b) {
        CurrentUser.requireStaff();
        Long trayId = Db.toLong(b.get("tray_id"));
        String date = Db.str(b.get("planned_date"));
        if (trayId == null || date == null) throw ApiException.badRequest("Cần tray_id và planned_date");
        Map<String, Object> h = db.one(
                "INSERT INTO harvest_schedules (tray_id, planned_date, note) VALUES (?, ?::date, ?) RETURNING *",
                trayId, date, Db.str(b.get("note")));
        db.update("UPDATE trays SET expected_harvest_date = ?::date WHERE id = ?", date, trayId);
        Long cid = notifications.trayCustomerId(trayId);
        if (cid != null) notifications.notifyCustomers("harvest", List.of(cid), "Lịch thu hoạch mới", "Dự kiến thu hoạch ngày " + date);
        return ResponseEntity.status(HttpStatus.CREATED).body(h);
    }

    /** Sửa lịch hoặc ghi nhận thu hoạch: { status: "harvested", yield_kg: 2.5 } */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        CurrentUser.requireStaff();
        String status = Db.str(b.get("status"));
        if (status != null && !status.matches("planned|harvested|cancelled")) throw ApiException.badRequest("status không hợp lệ");
        Double yield = Db.toDouble(b.get("yield_kg"));
        if ("harvested".equals(status) && yield == null) throw ApiException.badRequest("Cần sản lượng thu hoạch (kg)");
        Map<String, Object> h = db.one("""
                UPDATE harvest_schedules SET planned_date = COALESCE(?::date, planned_date), status = COALESCE(?, status),
                  yield_kg = COALESCE(?::numeric, yield_kg), note = COALESCE(?, note),
                  harvested_at = CASE WHEN ? = 'harvested' THEN now() ELSE harvested_at END
                WHERE id = ? RETURNING *""",
                Db.str(b.get("planned_date")), status, yield, Db.str(b.get("note")), status, id);
        if (h == null) throw ApiException.notFound("Không tìm thấy lịch thu hoạch");
        if ("harvested".equals(status)) {
            Long trayId = Db.toLong(h.get("tray_id"));
            // Đã thu hoạch: khay chuyển sang "harvested" (không còn là "sẵn sàng thu hoạch")
            db.update("UPDATE trays SET status = 'harvested' WHERE id = ? AND status <> 'empty'", trayId);
            Long cid = notifications.trayCustomerId(trayId);
            if (cid != null) notifications.notifyCustomers("harvest", List.of(cid), "Đã thu hoạch",
                    "Sản lượng: " + h.get("yield_kg") + " kg. Bạn có thể yêu cầu nhận tại trang trại hoặc giao tận nơi.");
        }
        return h;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        CurrentUser.requireStaff();
        if (db.update("DELETE FROM harvest_schedules WHERE id = ?", id) == 0) throw ApiException.notFound("Không tìm thấy");
        return Map.of("ok", true);
    }
}
