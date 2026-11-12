package com.mushroom.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mushroom.entity.Threshold;

public interface ThresholdRepository extends JpaRepository<Threshold, Long> {
    Optional<Threshold> findByRoomId(Long roomId);
    Optional<Threshold> findFirstByRoomIdIsNull();
}
