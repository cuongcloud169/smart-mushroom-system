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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mushroom.common.ApiException;
import com.mushroom.common.Db;
import com.mushroom.entity.Device;
import com.mushroom.security.CurrentUser;
import com.mushroom.service.IotService;

/**
 * API IoT.
 * - Cho ESP32 (header x-device-key): gửi dữ liệu cảm biến, hỏi lệnh điều khiển.
 * - Cho Admin/Operator: xem số đo, lịch sử (biểu đồ), điều khiển thiết bị, trạng thái thiết bị, cảnh báo.
 */
@RestController
@RequestMapping("/api/iot")
public class IotController {
    private final IotService iot;
    private final Db db;

    public IotController(IotService iot, Db db) {
        this.iot = iot;
        this.db = db;
    }

    // ---------------- ESP32 ----------------

    /** ESP32 gửi {temperature, humidity, co2}; phản hồi chứa danh sách lệnh bật/tắt relay */
    @PostMapping("/readings")
    public ResponseEntity<Map<String, Object>> readings(@RequestHeader(value = "x-device-key", required = false) String key,
                                                        @RequestBody Map<String, Object> body) {
        Device d = iot.authenticate(key);
        Map<String, Object> res = iot.ingest(d, Db.toDouble(body.get("temperature")),
                Db.toDouble(body.get("humidity")), Db.toDouble(body.get("co2")));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /** ESP32 hỏi lệnh hiện tại (gọi mỗi vài giây để lệnh bật/tắt tay đến nhanh) */
    @GetMapping("/commands")
    public Map<String, Object> commands(@RequestHeader(value = "x-device-key", required = false) String key) {
        Device d = iot.authenticate(key);
        iot.touch(d.getId());
        return Map.of("commands", iot.commands(d.getRoomId()));
    }

    // ---------------- Web Admin / Operator ----------------

    @GetMapping("/rooms/{id}/readings")
    public List<Map<String, Object>> roomReadings(@PathVariable Long id,
                                                  @RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to,
                                                  @RequestParam(defaultValue = "200") int limit) {
        CurrentUser.requireStaff();
        return db.list("""
                SELECT temperature, humidity, co2, recorded_at FROM sensor_readings
                WHERE room_id = ? AND recorded_at >= COALESCE(?::timestamptz, '-infinity')
                  AND recorded_at <= COALESCE(?::timestamptz, 'infinity')
                ORDER BY recorded_at DESC LIMIT ?""", id, from, to, Math.max(1, Math.min(limit, 5000)));
    }

    @GetMapping("/rooms/{id}/latest")
    public Map<String, Object> latest(@PathVariable Long id) {
        CurrentUser.requireStaff();
        return db.one("SELECT * FROM sensor_readings WHERE room_id = ? ORDER BY recorded_at DESC LIMIT 1", id);
    }

    /** Lịch sử gộp theo giờ (hoặc theo ngày nếu > 72 giờ) để vẽ biểu đồ */
    @GetMapping("/rooms/{id}/history")
    public List<Map<String, Object>> history(@PathVariable Long id, @RequestParam(defaultValue = "24") int hours) {
        CurrentUser.requireStaff();
        int h = Math.max(1, Math.min(hours, 24 * 90));
        String bucket = h > 72 ? "day" : "hour";
        return db.list("""
                SELECT date_trunc(?, recorded_at) AS time,
                       round(avg(temperature), 2) AS temperature, round(avg(humidity), 2) AS humidity,
                       round(avg(co2), 0) AS co2, count(*) AS samples
                FROM sensor_readings
                WHERE room_id = ? AND recorded_at > now() - make_interval(hours => ?)
                GROUP BY 1 ORDER BY 1""", bucket, id, h);
    }

    /** Điều khiển thủ công: { "state": true|false, "mode": "manual"|"auto" } */
    @PostMapping("/devices/{id}/control")
    public Map<String, Object> control(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CurrentUser.requireStaff();
        Object s = body.get("state");
        Boolean state = s == null ? null : (s instanceof Boolean b ? b : Boolean.valueOf(s.toString()));
        return iot.control(id, state, Db.str(body.get("mode")));
    }

    /** Trạng thái thiết bị; online = liên lạc trong 2 phút gần nhất */
    @GetMapping("/devices/status")
    public List<Map<String, Object>> deviceStatus() {
        CurrentUser.requireStaff();
        return db.list("""
                SELECT d.id, d.room_id, rm.name AS room_name, d.tray_id, d.name, d.type, d.actuator_kind, d.stream_url,
                       d.state, d.mode, d.is_active, d.last_seen,
                       (d.last_seen IS NOT NULL AND d.last_seen > now() - interval '2 minutes') AS online
                FROM devices d JOIN rooms rm ON rm.id = d.room_id ORDER BY d.room_id, d.id""");
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts(@RequestParam(required = false) Boolean resolved) {
        CurrentUser.requireStaff();
        return db.list("""
                SELECT a.*, rm.name AS room_name FROM alerts a JOIN rooms rm ON rm.id = a.room_id
                WHERE (?::boolean IS NULL OR a.resolved = ?::boolean) ORDER BY a.created_at DESC LIMIT 200""",
                resolved, resolved);
    }

    @PutMapping("/alerts/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable Long id) {
        CurrentUser.requireStaff();
        Map<String, Object> a = db.one("UPDATE alerts SET resolved = TRUE WHERE id = ? RETURNING *", id);
        if (a == null) throw ApiException.notFound("Không tìm thấy cảnh báo");
        return a;
    }
}
