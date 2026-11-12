package com.mushroom.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mushroom.common.ApiException;
import com.mushroom.common.Db;
import com.mushroom.entity.Tray;
import com.mushroom.repository.TrayRepository;
import com.mushroom.security.AuthUser;
import com.mushroom.security.CurrentUser;
import com.mushroom.service.NotificationService;

import jakarta.validation.Validator;

/**
 * Khay trồng nấm.
 * - CRUD chuẩn cho Admin/Operator (kế thừa CrudController); mã khay tự sinh nếu bỏ trống.
 * - Khách: xem khay trống, khay của tôi, chi tiết, môi trường, camera của khay mình thuê.
 * - Operator cập nhật tiến độ -> ghi nhật ký + thông báo cho khách.
 */
@RestController
@RequestMapping("/api/trays")
public class TrayController extends CrudController<Tray> {
    private final TrayRepository trays;
    private final Db db;
    private final NotificationService notifications;

    public TrayController(TrayRepository repo, ObjectMapper mapper, Validator validator, Db db, NotificationService notifications) {
        super(repo, mapper, validator, Tray.class);
        this.trays = repo;
        this.db = db;
        this.notifications = notifications;
    }

    @Override
    protected Sort sort() {
        return Sort.by("code");
    }

    /** Sinh mã khay duy nhất: TRAY-<id phòng>-<số thứ tự 3 chữ số> */
    @Override
    protected void beforeSave(Tray t, boolean isNew) {
        if (isNew && (t.getCode() == null || t.getCode().isBlank()) && t.getRoomId() != null) {
            long n = trays.countByRoomId(t.getRoomId()) + 1;
            String code;
            do {
                code = "TRAY-%d-%03d".formatted(t.getRoomId(), n++);
            } while (trays.existsByCode(code));
            t.setCode(code);
        }
        if (t.getCode() == null || t.getCode().isBlank()) throw ApiException.badRequest("Cần mã khay hoặc phòng");
        t.setCode(t.getCode().trim().toUpperCase());
    }

    /** Khay còn trống ở các phòng đang hoạt động (cho khách chọn thuê) */
    @GetMapping("/available")
    public List<Map<String, Object>> available() {
        CurrentUser.get();
        return db.list("""
                SELECT t.id, t.code, t.mushroom_type, rm.id AS room_id, rm.name AS room_name
                FROM trays t JOIN rooms rm ON rm.id = t.room_id
                WHERE t.status = 'empty' AND rm.status = 'active' ORDER BY t.code""");
    }

    /** Khay của khách: đang thuê (active) và đang chờ duyệt (pending) */
    @GetMapping("/mine")
    public List<Map<String, Object>> mine() {
        AuthUser u = CurrentUser.require("customer");
        return db.list("""
                SELECT t.*, rm.name AS room_name, rt.id AS rental_id, rt.status AS rental_status,
                       rt.start_date, rt.end_date
                FROM rentals rt JOIN trays t ON t.id = rt.tray_id JOIN rooms rm ON rm.id = t.room_id
                WHERE rt.customer_id = ? AND rt.status IN ('pending','active') ORDER BY t.code""", u.id());
    }

    /** Chi tiết khay + nhật ký tiến độ + lịch thu hoạch */
    @GetMapping("/{id}/detail")
    public Map<String, Object> detail(@PathVariable Long id) {
        checkAccess(id);
        Map<String, Object> tray = db.one("""
                SELECT t.*, rm.name AS room_name FROM trays t JOIN rooms rm ON rm.id = t.room_id WHERE t.id = ?""", id);
        if (tray == null) throw ApiException.notFound("Không tìm thấy khay");
        Map<String, Object> out = new LinkedHashMap<>(tray);
        out.put("progress_logs", db.list("""
                SELECT growth_stage, status, note, created_at FROM tray_progress_logs
                WHERE tray_id = ? ORDER BY created_at DESC LIMIT 50""", id));
        out.put("harvests", db.list("SELECT * FROM harvest_schedules WHERE tray_id = ? ORDER BY planned_date", id));
        return out;
    }

    /** Dữ liệu môi trường của phòng chứa khay (mới nhất trước) */
    @GetMapping("/{id}/readings")
    public List<Map<String, Object>> readings(@PathVariable Long id, @RequestParam(defaultValue = "100") int limit) {
        checkAccess(id);
        return db.list("""
                SELECT s.temperature, s.humidity, s.co2, s.recorded_at FROM sensor_readings s
                JOIN trays t ON t.room_id = s.room_id WHERE t.id = ? ORDER BY s.recorded_at DESC LIMIT ?""",
                id, Math.max(1, Math.min(limit, 1000)));
    }

    /** Camera của khay (ưu tiên camera gắn riêng cho khay, sau đó camera của phòng) */
    @GetMapping("/{id}/camera")
    public List<Map<String, Object>> camera(@PathVariable Long id) {
        checkAccess(id);
        return db.list("""
                SELECT d.id, d.name, d.stream_url FROM devices d JOIN trays t ON t.room_id = d.room_id
                WHERE t.id = ? AND d.type = 'camera' AND d.is_active AND (d.tray_id IS NULL OR d.tray_id = t.id)
                ORDER BY (d.tray_id IS NOT NULL) DESC""", id);
    }

    /** Operator cập nhật tiến độ/trạng thái khay -> ghi nhật ký + thông báo cho khách đang thuê */
    @PutMapping("/{id}/progress")
    public Map<String, Object> progress(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        AuthUser u = CurrentUser.requireStaff();
        String status = Db.str(b.get("status"));
        if (status != null && !status.matches(Tray.STATUSES)) throw ApiException.badRequest("Trạng thái khay không hợp lệ");
        Object fill = b.get("fill_percent");
        Integer fillPct = fill == null || Db.str(fill) == null ? null : Integer.valueOf(Db.str(fill));
        if (fillPct != null && (fillPct < 0 || fillPct > 100)) throw ApiException.badRequest("% lấp đầy phải từ 0 đến 100");

        Map<String, Object> t = db.one("""
                UPDATE trays SET growth_stage = COALESCE(?, growth_stage), status = COALESCE(?, status),
                  fill_percent = COALESCE(?::int, fill_percent),
                  expected_harvest_date = COALESCE(?::date, expected_harvest_date),
                  mushroom_type = COALESCE(?, mushroom_type)
                WHERE id = ? RETURNING *""",
                Db.str(b.get("growth_stage")), status, fillPct, Db.str(b.get("expected_harvest_date")),
                Db.str(b.get("mushroom_type")), id);
        if (t == null) throw ApiException.notFound("Không tìm thấy khay");
        db.update("INSERT INTO tray_progress_logs (tray_id, operator_id, growth_stage, status, note) VALUES (?, ?, ?, ?, ?)",
                id, u.id(), Db.str(b.get("growth_stage")), status, Db.str(b.get("note")));

        Long cid = notifications.trayCustomerId(id);
        if (cid != null) {
            boolean ready = "ready_harvest".equals(t.get("status"));
            String note = Db.str(b.get("note"));
            notifications.notifyCustomers(ready ? "harvest" : "progress", List.of(cid),
                    ready ? "Khay " + t.get("code") + " sẵn sàng thu hoạch" : "Cập nhật tiến độ khay " + t.get("code"),
                    note != null ? note : "Giai đoạn: " + (t.get("growth_stage") == null ? "-" : t.get("growth_stage")));
        }
        return t;
    }

    /** Khách chỉ truy cập khay mình đang thuê; nhân viên truy cập tất cả. */
    private void checkAccess(Long trayId) {
        AuthUser u = CurrentUser.get();
        if (u.isStaff()) return;
        long n = db.count("SELECT count(*) FROM rentals WHERE tray_id = ? AND customer_id = ? AND status = 'active'", trayId, u.id());
        if (n == 0) throw ApiException.forbidden("Bạn không thuê khay này");
    }
}
