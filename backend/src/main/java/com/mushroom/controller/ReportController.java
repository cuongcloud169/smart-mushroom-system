package com.mushroom.controller;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mushroom.common.ApiException;
import com.mushroom.common.Db;
import com.mushroom.security.CurrentUser;

/**
 * Thống kê và báo cáo cho Admin/Operator. Thêm ?format=csv để tải file (mở bằng Excel, đúng tiếng Việt).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private static final Set<String> EXPORTABLE = Set.of("users", "rooms", "trays", "rentals", "transactions",
            "sensor_readings", "alerts", "harvest_schedules", "delivery_requests", "devices");

    private final Db db;

    public ReportController(Db db) {
        this.db = db;
    }

    /** Số liệu tổng quan cho Dashboard */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        CurrentUser.requireStaff();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rooms", db.count("SELECT count(*) FROM rooms"));
        m.put("trays_by_status", db.list("SELECT status, count(*) AS n FROM trays GROUP BY status ORDER BY status"));
        m.put("active_rentals", db.count("SELECT count(*) FROM rentals WHERE status = 'active'"));
        m.put("pending_rentals", db.count("SELECT count(*) FROM rentals WHERE status = 'pending'"));
        m.put("open_alerts", db.count("SELECT count(*) FROM alerts WHERE NOT resolved"));
        m.put("revenue_total", db.one("SELECT COALESCE(sum(amount), 0) AS total FROM transactions WHERE status = 'paid'").get("total"));
        m.put("customers", db.count("SELECT count(*) FROM users WHERE role = 'customer'"));
        m.put("pending_deliveries", db.count("SELECT count(*) FROM delivery_requests WHERE status IN ('pending','confirmed','delivering')"));
        m.put("devices", db.one("""
                SELECT count(*) FILTER (WHERE last_seen > now() - interval '2 minutes') AS online, count(*) AS total
                FROM devices WHERE is_active AND type = 'controller'"""));
        return m;
    }

    /** Môi trường trung bình theo ngày, lọc theo phòng */
    @GetMapping("/environment")
    public ResponseEntity<?> environment(@RequestParam(defaultValue = "7") int days,
                                         @RequestParam(name = "room_id", required = false) Long roomId,
                                         @RequestParam(required = false) String format) {
        CurrentUser.requireStaff();
        List<Map<String, Object>> rows = db.list("""
                SELECT rm.name AS room, date_trunc('day', s.recorded_at)::date AS day,
                       round(avg(temperature), 2) AS avg_temp, round(avg(humidity), 2) AS avg_humidity,
                       round(avg(co2), 0) AS avg_co2, min(temperature) AS min_temp, max(temperature) AS max_temp,
                       count(*) AS samples
                FROM sensor_readings s JOIN rooms rm ON rm.id = s.room_id
                WHERE s.recorded_at > now() - make_interval(days => ?) AND (?::bigint IS NULL OR s.room_id = ?::bigint)
                GROUP BY rm.name, day ORDER BY day, room""", Math.max(1, Math.min(days, 365)), roomId, roomId);
        return send(rows, format, "bao-cao-moi-truong");
    }

    /** Cho thuê và doanh thu theo tháng */
    @GetMapping("/rentals")
    public ResponseEntity<?> rentals(@RequestParam(required = false) String format) {
        CurrentUser.requireStaff();
        List<Map<String, Object>> rows = db.list("""
                WITH r AS (
                  SELECT to_char(created_at, 'YYYY-MM') AS month, count(*) AS rentals,
                         count(*) FILTER (WHERE status = 'cancelled') AS cancelled
                  FROM rentals GROUP BY 1),
                tr AS (
                  SELECT to_char(created_at, 'YYYY-MM') AS month, sum(amount) AS revenue
                  FROM transactions WHERE status = 'paid' GROUP BY 1)
                SELECT r.month, r.rentals, r.cancelled, COALESCE(tr.revenue, 0) AS revenue
                FROM r LEFT JOIN tr ON tr.month = r.month ORDER BY r.month""");
        return send(rows, format, "bao-cao-cho-thue");
    }

    /** Sản lượng thu hoạch theo tháng và phòng */
    @GetMapping("/harvests")
    public ResponseEntity<?> harvests(@RequestParam(required = false) String format) {
        CurrentUser.requireStaff();
        List<Map<String, Object>> rows = db.list("""
                SELECT to_char(date_trunc('month', h.harvested_at), 'YYYY-MM') AS month, rm.name AS room,
                       count(*) AS harvests, COALESCE(sum(h.yield_kg), 0) AS total_kg
                FROM harvest_schedules h JOIN trays t ON t.id = h.tray_id JOIN rooms rm ON rm.id = t.room_id
                WHERE h.status = 'harvested' GROUP BY 1, 2 ORDER BY 1, 2""");
        return send(rows, format, "bao-cao-thu-hoach");
    }

    /** Xuất dữ liệu thô một bảng ra CSV (chỉ Admin) */
    @GetMapping("/export/{table}")
    public ResponseEntity<?> export(@PathVariable String table) {
        CurrentUser.requireAdmin();
        if (!EXPORTABLE.contains(table)) throw ApiException.badRequest("Bảng không được phép xuất");
        String cols = switch (table) {
            case "users" -> "id, email, full_name, phone, address, role, is_active, created_at";
            case "devices" -> "id, room_id, tray_id, name, type, actuator_kind, stream_url, state, mode, is_active, last_seen, created_at";
            default -> "*";
        };
        // Tên bảng đã được kiểm tra trong danh sách cho phép ở trên nên an toàn khi ghép chuỗi
        return send(db.list("SELECT " + cols + " FROM " + table + " ORDER BY 1 LIMIT 100000"), "csv", table);
    }

    private static ResponseEntity<?> send(List<Map<String, Object>> rows, String format, String name) {
        if (!"csv".equals(format)) return ResponseEntity.ok(rows);
        byte[] bytes = toCsv(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    /** CSV có BOM UTF-8 để Excel hiển thị đúng tiếng Việt */
    static String toCsv(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        if (rows.isEmpty()) return sb.toString();
        List<String> keys = List.copyOf(rows.get(0).keySet());
        sb.append(String.join(",", keys)).append("\n");
        for (Map<String, Object> r : rows) {
            for (int i = 0; i < keys.size(); i++) {
                Object v = r.get(keys.get(i));
                if (i > 0) sb.append(',');
                if (v != null) sb.append('"').append(v.toString().replace("\"", "\"\"")).append('"');
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
