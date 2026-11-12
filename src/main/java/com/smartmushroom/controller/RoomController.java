
package com.smartmushroom.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.smartmushroom.dto.RoomCreateRequestDTO;
import com.smartmushroom.dto.RoomUpdateRequestDTO;
import com.smartmushroom.dto.RoomResponseDTO;
import com.smartmushroom.entity.Room;
import com.smartmushroom.service.RoomService;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // Convert Entity to Response DTO
    private RoomResponseDTO convertToDTO(Room room) {
        return new RoomResponseDTO(
                room.getId(),
                room.getRoomCode(),
                room.getRoomName(),
                room.getDescription(),
                room.getLocation(),
                room.getStatus(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }

    // GET: Get all rooms
    @GetMapping
    public ResponseEntity<List<RoomResponseDTO>> getAllRooms() {
        List<RoomResponseDTO> rooms = roomService.getAllRooms()
                .stream()
                .map(this::convertToDTO)
                .toList();

        return ResponseEntity.ok(rooms);
    }

    // GET: Get room by ID
    @GetMapping("/{id}")
    public ResponseEntity<RoomResponseDTO> getRoomById(
            @PathVariable Long id) {

        return roomService.getRoomById(id)
                .map(room -> ResponseEntity.ok(convertToDTO(room)))
                .orElse(ResponseEntity.notFound().build());
    }

    // GET: Get room by code
    @GetMapping("/code/{roomCode}")
    public ResponseEntity<RoomResponseDTO> getRoomByCode(
            @PathVariable String roomCode) {

        Room room = roomService.getRoomByCode(roomCode);

        if (room == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(convertToDTO(room));
    }

    // POST: Create room
    @PostMapping
    public ResponseEntity<?> createRoom(
            @RequestBody RoomCreateRequestDTO request) {

        if (request.getRoomCode() == null
                || request.getRoomCode().isBlank()
                || request.getRoomName() == null
                || request.getRoomName().isBlank()) {

            return ResponseEntity.badRequest()
                    .body("Room code and room name are required.");
        }

        if (roomService.existsByRoomCode(request.getRoomCode())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Room code already exists.");
        }

        Room room = new Room();
        room.setRoomCode(request.getRoomCode().trim());
        room.setRoomName(request.getRoomName().trim());
        room.setDescription(request.getDescription());
        room.setLocation(request.getLocation());

        room.setStatus(
                request.getStatus() == null
                        || request.getStatus().isBlank()
                        ? "ACTIVE"
                        : request.getStatus().trim()
        );

        Room savedRoom = roomService.saveRoom(room);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(convertToDTO(savedRoom));
    }

    // PUT: Update room
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoom(
            @PathVariable Long id,
            @RequestBody RoomUpdateRequestDTO request) {

        Room room = roomService.getRoomById(id).orElse(null);

        if (room == null) {
            return ResponseEntity.notFound().build();
        }

        if (request.getRoomName() == null
                || request.getRoomName().isBlank()) {

            return ResponseEntity.badRequest()
                    .body("Room name is required.");
        }

        room.setRoomName(request.getRoomName().trim());
        room.setDescription(request.getDescription());
        room.setLocation(request.getLocation());

        if (request.getStatus() != null
                && !request.getStatus().isBlank()) {
            room.setStatus(request.getStatus().trim());
        }

        Room updatedRoom = roomService.saveRoom(room);

        return ResponseEntity.ok(convertToDTO(updatedRoom));
    }

    // DELETE: Delete room
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable Long id) {

        if (roomService.getRoomById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        roomService.deleteRoom(id);

        return ResponseEntity.noContent().build();
    }
}
