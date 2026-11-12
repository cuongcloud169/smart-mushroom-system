
package com.smartmushroom.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smartmushroom.entity.Room;
import com.smartmushroom.repository.RoomRepository;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    // Lấy danh sách tất cả phòng
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    // Tìm phòng theo ID
    public Optional<Room> getRoomById(Long id) {
        return roomRepository.findById(id);
    }

    // Tìm phòng theo mã
    public Room getRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode);
    }

    // Kiểm tra mã phòng đã tồn tại
    public boolean existsByRoomCode(String roomCode) {
        return roomRepository.existsByRoomCode(roomCode);
    }

    // Thêm hoặc lưu thông tin phòng
    public Room saveRoom(Room room) {
        return roomRepository.save(room);
    }

    // Xóa phòng theo ID
    public void deleteRoom(Long id) {
        roomRepository.deleteById(id);
    }
}
