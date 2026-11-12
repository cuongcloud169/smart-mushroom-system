package com.mushroom.config;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mushroom.entity.Device;
import com.mushroom.entity.RentalPackage;
import com.mushroom.entity.Room;
import com.mushroom.entity.Tray;
import com.mushroom.entity.User;
import com.mushroom.repository.DeviceRepository;
import com.mushroom.repository.RentalPackageRepository;
import com.mushroom.repository.RoomRepository;
import com.mushroom.repository.TrayRepository;
import com.mushroom.repository.UserRepository;

/**
 * Tạo dữ liệu mẫu khi CSDL còn trống (tắt bằng SEED_DEMO_DATA=false):
 * 3 tài khoản, 1 phòng, 6 khay, 2 gói thuê, 6 thiết bị. In API key của ESP32 ra log.
 */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final boolean enabled;
    private final UserRepository users;
    private final RoomRepository rooms;
    private final TrayRepository trays;
    private final RentalPackageRepository packages;
    private final DeviceRepository devices;
    private final PasswordEncoder encoder;

    public DataSeeder(@Value("${app.seed-demo-data}") boolean enabled, UserRepository users, RoomRepository rooms,
                      TrayRepository trays, RentalPackageRepository packages, DeviceRepository devices,
                      PasswordEncoder encoder) {
        this.enabled = enabled;
        this.users = users;
        this.rooms = rooms;
        this.trays = trays;
        this.packages = packages;
        this.devices = devices;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || users.count() > 0) return;

        user("admin@mushroom.local", "Admin@123", "Quản trị viên", "admin");
        user("operator@mushroom.local", "Operator@123", "Người vận hành", "operator");
        user("customer@mushroom.local", "Customer@123", "Khách hàng mẫu", "customer");

        Room room = new Room();
        room.setName("Phòng A1");
        room.setDescription("Phòng nuôi nấm sò");
        room.setCapacity(10);
        rooms.save(room);

        for (int i = 1; i <= 6; i++) {
            Tray t = new Tray();
            t.setCode("TRAY-A1-%03d".formatted(i));
            t.setRoomId(room.getId());
            t.setMushroomType("Nấm sò");
            trays.save(t);
        }

        pkg("Gói 1 tháng", "Thuê 1 khay trong 30 ngày", 30, "300000");
        pkg("Gói 3 tháng", "Thuê 1 khay trong 90 ngày", 90, "800000");

        Device esp = device(room.getId(), "ESP32 Phòng A1", "controller", null, null);
        device(room.getId(), "Camera A1", "camera", null, "http://192.168.1.50:8080/stream");
        device(room.getId(), "Phun sương A1", "actuator", "mist", null);
        device(room.getId(), "Quạt thông gió A1", "actuator", "fan", null);
        device(room.getId(), "Đèn LED A1", "actuator", "light", null);
        device(room.getId(), "Sưởi A1", "actuator", "heater", null);

        log.info("=== Đã tạo dữ liệu mẫu ===");
        log.info("Tài khoản: admin@mushroom.local / Admin@123 | operator@mushroom.local / Operator@123 | customer@mushroom.local / Customer@123");
        log.info("API key của ESP32 Phòng A1: {}", esp.getApiKey());
    }

    private void user(String email, String pw, String name, String role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(pw));
        u.setFullName(name);
        u.setRole(role);
        users.save(u);
    }

    private void pkg(String name, String desc, int days, String price) {
        RentalPackage p = new RentalPackage();
        p.setName(name);
        p.setDescription(desc);
        p.setDurationDays(days);
        p.setPrice(new BigDecimal(price));
        packages.save(p);
    }

    private Device device(Long roomId, String name, String type, String kind, String url) {
        Device d = new Device();
        d.setRoomId(roomId);
        d.setName(name);
        d.setType(type);
        d.setActuatorKind(kind);
        d.setStreamUrl(url);
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        d.setApiKey(HexFormat.of().formatHex(b));
        return devices.save(d);
    }
}
