package com.mushroom.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thiết bị IoT của một phòng:
 * - controller: bo mạch ESP32 (gửi dữ liệu, nhận lệnh), xác thực bằng apiKey
 * - sensor: cảm biến riêng (nếu cần quản lý tách biệt)
 * - camera: camera IP, streamUrl là đường dẫn xem hình
 * - actuator: thiết bị điều khiển, actuatorKind = mist | fan | light | heater
 */
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Cần chọn phòng")
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "tray_id")
    private Long trayId;

    @NotBlank(message = "Tên thiết bị không được trống")
    @Column(nullable = false)
    private String name;

    @NotNull(message = "Cần loại thiết bị")
    @Pattern(regexp = "controller|sensor|camera|actuator", message = "Loại thiết bị không hợp lệ")
    @Column(nullable = false)
    private String type;

    @Pattern(regexp = "mist|fan|light|heater", message = "Loại thiết bị điều khiển không hợp lệ")
    @Column(name = "actuator_kind")
    private String actuatorKind;

    @Column(name = "stream_url")
    private String streamUrl;

    /** Khóa xác thực của ESP32: chỉ xem qua API riêng dành cho Admin */
    @JsonIgnore
    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(nullable = false)
    private Boolean state = false;

    @Pattern(regexp = "auto|manual")
    @Column(nullable = false)
    private String mode = "auto";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_seen")
    private OffsetDateTime lastSeen;

    @JsonIgnore
    @Column(name = "offline_notified", nullable = false)
    private Boolean offlineNotified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
