package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResponse {
    private String incidentId;
    private String sessionId;
    private String ticketCode;
    private String vehiclePlate;
    private String incidentType;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private String reporterId;
    private String reportSource;
    private String resolution;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private String resolutionAction;
    private String verificationResult;
    private LocalDateTime verifiedAt;
    private String verifiedBy;
}