package com.mushroom;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động backend.
 * - @EnableScheduling: bật các job chạy định kỳ (hết hạn hợp đồng, thiết bị offline).
 * - Bỏ UserDetailsServiceAutoConfiguration vì hệ thống tự xác thực bằng JWT.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class MushroomApplication {
    public static void main(String[] args) {
        // Cố định múi giờ Việt Nam. Một số máy báo tên cũ "Asia/Saigon" mà PostgreSQL không nhận -> lỗi kết nối.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(MushroomApplication.class, args);
    }
}
