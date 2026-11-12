package com.mushroom.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mushroom.entity.Device;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByApiKeyAndIsActiveTrue(String apiKey);
}
