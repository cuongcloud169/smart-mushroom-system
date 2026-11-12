package com.mushroom.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khay trồng nấm. code là mã định danh duy nhất để khách đăng ký khay. */
@Entity
@Table(name = "trays")
@Getter
@Setter
@NoArgsConstructor
public class Tray {
    public static final String STATUSES = "empty|rented|growing|ready_harvest|harvested|maintenance";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bỏ trống khi tạo: hệ thống tự sinh dạng TRAY-<phòng>-<số thứ tự> */
    @Column(nullable = false, unique = true)
    private String code;

    @NotNull(message = "Cần chọn phòng")
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Pattern(regexp = STATUSES, message = "Trạng thái khay không hợp lệ")
    @Column(nullable = false)
    private String status = "empty";

    @Min(0)
    @Max(100)
    @Column(name = "fill_percent", nullable = false)
    private Integer fillPercent = 0;

    @Column(name = "mushroom_type")
    private String mushroomType;

    @Column(name = "growth_stage")
    private String growthStage;

    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
