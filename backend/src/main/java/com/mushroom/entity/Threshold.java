package com.mushroom.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ngưỡng môi trường. roomId = null là ngưỡng mặc định cho mọi phòng. */
@Entity
@Table(name = "thresholds")
@Getter
@Setter
@NoArgsConstructor
public class Threshold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", unique = true)
    private Long roomId;

    @NotNull @Column(name = "temp_min", nullable = false) private BigDecimal tempMin = new BigDecimal("20");
    @NotNull @Column(name = "temp_max", nullable = false) private BigDecimal tempMax = new BigDecimal("28");
    @NotNull @Column(name = "hum_min", nullable = false) private BigDecimal humMin = new BigDecimal("80");
    @NotNull @Column(name = "hum_max", nullable = false) private BigDecimal humMax = new BigDecimal("95");
    @NotNull @Column(name = "co2_max", nullable = false) private BigDecimal co2Max = new BigDecimal("1000");

    /** Giờ bật/tắt đèn LED tự động (0-23) */
    @Min(0) @Max(23) @Column(name = "light_on_hour", nullable = false) private Integer lightOnHour = 6;
    @Min(0) @Max(23) @Column(name = "light_off_hour", nullable = false) private Integer lightOffHour = 18;
}
