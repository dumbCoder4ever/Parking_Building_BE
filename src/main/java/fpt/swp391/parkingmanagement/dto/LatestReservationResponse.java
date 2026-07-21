package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatestReservationResponse {
    private String reservationId;
    private String reservationCode;
    private String reservationStatus;
    private java.time.LocalDateTime reservationStart;
    private java.time.LocalDateTime createdAt;
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private String zoneId;
    private String zoneName;
    private String slotId;
    private String slotName;
    private String vehicleId;
    private String vehiclePlate;
    private String vehicleType;
    private String driverUserId;
    private String driverEmail;
    private String driverFullName;
}