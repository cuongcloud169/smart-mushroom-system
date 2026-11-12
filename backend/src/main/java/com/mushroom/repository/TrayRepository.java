package com.mushroom.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mushroom.entity.Tray;

public interface TrayRepository extends JpaRepository<Tray, Long> {
    boolean existsByCode(String code);
    long countByRoomId(Long roomId);
}
