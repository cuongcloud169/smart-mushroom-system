package com.mushroom.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mushroom.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

}
