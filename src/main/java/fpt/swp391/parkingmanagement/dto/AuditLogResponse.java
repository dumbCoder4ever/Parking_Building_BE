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
public class AuditLogResponse {
    private String logId;
    private String actionType;
    private String entityType;
    private String entityId;
    private String userId;
    private String username;
    private String description;
    private String oldValue;
    private String newValue;
    private String buildingId;
    private String metadata;
    private LocalDateTime createdAt;
}
