
package com.smartmushroom.dto;

import java.time.OffsetDateTime;

public class RoomResponseDTO {

    private Long id;
    private String roomCode;
    private String roomName;
    private String description;
    private String location;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public RoomResponseDTO(
            Long id,
            String roomCode,
            String roomName,
            String description,
            String location,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        this.id = id;
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.description = description;
        this.location = location;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
