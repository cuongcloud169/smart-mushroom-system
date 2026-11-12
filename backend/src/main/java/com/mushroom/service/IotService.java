package com.mushroom.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mushroom.common.ApiException;
import com.mushroom.common.Db;
import com.mushroom.entity.Device;
import com.mushroom.entity.Threshold;
import com.mushroom.repository.DeviceRepository;
import com.mushroom.repository.ThresholdRepository;
import com.mushroom.service.EnvironmentRules.Issue;
import com.mushroom.service.EnvironmentRules.Thresholds;

/**
 * Trái tim của phần giám sát. Khi ESP32 gửi dữ liệu:
 * 1) xác thực thiết bị bằng x-device-key -> biết phòng nào
 * 2) kiểm tra số đo hợp lệ, lưu vào sensor_readings
 * 3) vượt ngưỡng -> tạo cảnh báo + thông báo (không lặp lại trong 10 phút)
 * 4) thiết bị ở chế độ auto được bật/tắt theo ngưỡng
 * 5) trả về danh sách lệnh để ESP32 bật/tắt relay
 */
@Service
public class IotService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final Db db;
    private final DeviceRepository devices;
    private final ThresholdRepository thresholds;
    private final NotificationService notifications;

    public IotService(Db db, DeviceRepository devices, ThresholdRepository thresholds, NotificationService notifications) {
        this.db = db;
        this.devices = devices;
        this.thresholds = thresholds;
        this.notifications = notifications;
    }

    public Device authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw ApiException.unauthorized("Thiếu x-device-key");
        return devices.findByApiKeyAndIsActiveTrue(apiKey)
                .orElseThrow(() -> ApiException.unauthorized("Thiết bị không hợp lệ"));
    }

    /** Ngưỡng của phòng; nếu phòng chưa cấu hình thì dùng ngưỡng mặc định. */
    public Thresholds thresholdsOf(Long roomId) {
        Threshold t = thresholds.findByRoomId(roomId)
                .or(thresholds::findFirstByRoomIdIsNull)
                .orElseGet(Threshold::new);
        return new Thresholds(t.getTempMin().doubleValue(), t.getTempMax().doubleValue(),
                t.getHumMin().doubleValue(), t.getHumMax().doubleValue(), t.getCo2Max().doubleValue(),
                t.getLightOnHour(), t.getLightOffHour());
    }

    @Transactional
    public Map<String, Object> ingest(Device device, Double t, Double h, Double c) {
        String err = EnvironmentRules.validate(t, h, c);
        if (err != null) throw ApiException.badRequest(err);

        Long roomId = device.getRoomId();
        db.update("INSERT INTO sensor_readings (room_id, device_id, temperature, humidity, co2) VALUES (?, ?, ?, ?, ?)",
                roomId, device.getId(), t, h, c);
        touch(device.getId());

        Thresholds th = thresholdsOf(roomId);
        List<String> created = createAlerts(roomId, EnvironmentRules.checkIssues(t, h, c, th));
        autoControl(roomId, t, h, c, th);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("alerts", created);
        res.put("commands", commands(roomId));
        return res;
    }

    /** Cập nhật thời điểm liên lạc cuối và bỏ cờ "đã báo offline". */
    public void touch(Long deviceId) {
        db.update("UPDATE devices SET last_seen = now(), offline_notified = FALSE WHERE id = ?", deviceId);
    }

    private List<String> createAlerts(Long roomId, List<Issue> issues) {
        List<String> created = new ArrayList<>();
        for (Issue i : issues) {
            long dup = db.count("""
                    SELECT count(*) FROM alerts WHERE room_id = ? AND type = ? AND NOT resolved
                    AND created_at > now() - interval '10 minutes'""", roomId, i.type());
            if (dup > 0) continue;
            db.update("INSERT INTO alerts (room_id, type, message, value) VALUES (?, ?, ?, ?)",
                    roomId, i.type(), i.message(), i.value());
            Map<String, Object> room = db.one("SELECT name FROM rooms WHERE id = ?", roomId);
            String roomName = room == null ? "#" + roomId : (String) room.get("name");
            notifications.notifyEvent("env_alert", notifications.roomCustomerIds(roomId),
                    "Cảnh báo môi trường - " + roomName, i.message());
            created.add(i.type());
        }
        return created;
    }

    private void autoControl(Long roomId, Double t, Double h, Double c, Thresholds th) {
        int hour = LocalTime.now(ZONE).getHour();
        List<Map<String, Object>> acts = db.list(
                "SELECT id, actuator_kind, state FROM devices WHERE room_id = ? AND type = 'actuator' AND mode = 'auto' AND is_active",
                roomId);
        for (Map<String, Object> d : acts) {
            boolean cur = Boolean.TRUE.equals(d.get("state"));
            boolean next = EnvironmentRules.nextState((String) d.get("actuator_kind"), cur, t, h, c, th, hour);
            if (next != cur) db.update("UPDATE devices SET state = ? WHERE id = ?", next, d.get("id"));
        }
    }

    /** Danh sách lệnh bật/tắt hiện tại cho các thiết bị điều khiển của phòng. */
    public List<Map<String, Object>> commands(Long roomId) {
        return db.list("""
                SELECT id AS device_id, actuator_kind AS kind, state, mode FROM devices
                WHERE room_id = ? AND type = 'actuator' AND is_active ORDER BY id""", roomId);
    }

    /** Điều khiển thủ công: { state, mode }. Bật/tắt tay sẽ chuyển thiết bị sang chế độ manual. */
    public Map<String, Object> control(Long deviceId, Boolean state, String mode) {
        if (mode != null && !mode.equals("auto") && !mode.equals("manual")) throw ApiException.badRequest("mode phải là auto hoặc manual");
        String newMode = mode != null ? mode : (state != null ? "manual" : null);
        Map<String, Object> row = db.one("""
                UPDATE devices SET state = COALESCE(?::boolean, state), mode = COALESCE(?::text, mode)
                WHERE id = ? AND type = 'actuator' RETURNING id, room_id, name, actuator_kind, state, mode""",
                state, newMode, deviceId);
        if (row == null) throw ApiException.notFound("Không tìm thấy thiết bị điều khiển");
        return row;
    }

    /** Mỗi phút: thiết bị không liên lạc quá 2 phút thì báo cho nhân viên (mỗi lần mất kết nối báo 1 lần). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void detectOffline() {
        List<Map<String, Object>> rows = db.list("""
                UPDATE devices SET offline_notified = TRUE
                WHERE is_active AND type IN ('controller','camera') AND last_seen IS NOT NULL
                  AND last_seen < now() - interval '2 minutes' AND NOT offline_notified
                RETURNING name""");
        for (Map<String, Object> r : rows) {
            notifications.notifyStaff("device_offline", "Thiết bị mất kết nối",
                    "Thiết bị \"" + r.get("name") + "\" không gửi dữ liệu quá 2 phút");
        }
    }
}
