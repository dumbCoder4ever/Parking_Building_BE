package fpt.swp391.parkingmanagement.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Long userId;
    private String username;
    private String fullName;
    private String gmail;
    private String phone;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
