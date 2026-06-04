package fpt.swp391.parkingmanagement.dto;

import fpt.swp391.parkingmanagement.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriverSummaryResponse {

    private String userId;
    private String username;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String status;

    public static DriverSummaryResponse from(User user) {
        return DriverSummaryResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus())
                .build();
    }
}
