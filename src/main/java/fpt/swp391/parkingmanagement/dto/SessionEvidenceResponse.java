package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEvidenceResponse {
    private String incidentId;
    private String sessionId;
    private String sessionStatus;
    private boolean sessionActive;
    private LocalDateTime checkinTime;
    private String checkinVehicleImage;
    private String checkoutVehicleImage;

    private String vehicleId;
    private String vehiclePlate;
    private String vehicleType;
    private String driverUserId;
    private String driverEmail;
    private String driverFullName;
    /** True when driver report reporter matches session driver account. */
    private Boolean driverMatchesReporter;

    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private String zoneId;
    private String zoneName;
    private String slotId;
    private String slotName;

    private String ticketCode;
    private BigDecimal sessionEstimatedFee;
    private BigDecimal sessionTotalFee;
    private String sessionPaymentStatus;

    /** Reservation linked to session or latest active reservation — supplementary only. */
    private LatestReservationResponse reservation;
}
