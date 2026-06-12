package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ReservationResponse {
    private String reservationId;
    private String reservationCode;
    private String reservationStatus;
    private String reservationNote;
    private LocalDateTime reservationStart;
    private LocalDateTime reservationEnd;
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private String floorVehicleTypeId;
    private String floorVehicleTypeName;
    private String zoneId;
    private String zoneName;
    private String zoneStatus;
    private String slotId;
    private String slotName;
    private String slotStatus;
    private String ticketCode;
    private String qrCode;
}
