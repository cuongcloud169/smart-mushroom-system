package com.mushroom.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Gói thuê khay và giá */
@Entity
@Table(name = "rental_packages")
@Getter
@Setter
@NoArgsConstructor
public class RentalPackage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Tên gói không được trống")
    @Column(nullable = false)
    private String name;

    private String description;

    @NotNull(message = "Cần số ngày thuê")
    @Min(value = 1, message = "Số ngày thuê phải lớn hơn 0")
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @NotNull(message = "Cần giá thuê")
    @DecimalMin(value = "0", message = "Giá không được âm")
    @Column(nullable = false)
    private BigDecimal price;

    /** false = ngừng bán, khách không thấy gói này */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
