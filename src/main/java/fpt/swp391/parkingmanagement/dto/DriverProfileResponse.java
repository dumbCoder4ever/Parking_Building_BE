package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverProfileResponse {

    private String userId;
    private String username;
    private String email;
    private String phoneNumber;
    private String fullName;
    private String avatarUrl;
    private String role;
    private String status;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private int totalVehicles;
    private int totalReservations;
    private int totalSessions;
}
