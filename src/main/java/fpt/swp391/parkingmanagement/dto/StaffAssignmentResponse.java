package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffAssignmentResponse {

    private String assignmentId;
    private String userId;
    private String username;
    private String fullName;
    private String email;
    private String buildingId;
    private String buildingName;
    private LocalDateTime assignedAt;
}
