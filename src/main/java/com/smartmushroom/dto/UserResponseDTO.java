
package com.smartmushroom.dto;

import java.time.OffsetDateTime;

public class UserResponseDTO {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UserResponseDTO() {
    }

    public UserResponseDTO(
            Long id,
            String fullName,
            String email,
            String phone,
            String role,
            Boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getRole() {
        return role;
    }

    public Boolean getActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
