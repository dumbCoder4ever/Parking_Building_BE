package fpt.swp391.parkingmanagement.dto;

import fpt.swp391.parkingmanagement.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileResponse {

    private String userId;
    private String username;
    private String fullName;
    private String phoneNumber;
    private String gmail;
    private String avatarUrl;
    private String role;
    private String status;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .gmail(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
