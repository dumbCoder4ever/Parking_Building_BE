package fpt.swp391.parkingmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "incidents")
public class Incident {

    @Id
    @Column(name = "incident_id", length = 36)
    private String incidentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ParkingSession session;

    @Column(name = "incident_type", length = 50)
    private String incidentType;

    private String description;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reporter_id", length = 100)
    private String reporterId;

    @Column(name = "report_source", length = 20)
    private String reportSource;

    @Column(length = 500)
    private String resolution;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolution_action", length = 50)
    private String resolutionAction;

    // Verification fields (for DRIVER_LOST_TICKET)
    @Column(name = "verified_plate_number", length = 20)
    private String verifiedPlateNumber;

    @Column(name = "verified_ticket_code", length = 50)
    private String verifiedTicketCode;

    @Column(name = "verification_result", length = 20)
    private String verificationResult;  // MATCH / MISMATCH / PENDING

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    // Workflow tracking
    @Column(name = "previous_status", length = 20)
    private String previousStatus;

    @PrePersist
    public void prePersist() {
        if (incidentId == null) incidentId = UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }
}
