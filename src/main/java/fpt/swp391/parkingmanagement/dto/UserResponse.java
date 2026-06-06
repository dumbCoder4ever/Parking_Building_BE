package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserResponse {
    private String userId;
    private String username;
    private String fullName;
    private String gmail;
    private String phoneNumber;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
