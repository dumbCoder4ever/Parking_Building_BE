package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlotStatusResponse {

    private String slotId;
    private String slotName;
    private String slotStatus;
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private String zoneId;
    private String zoneName;
    private String zoneStatus;
    private String reservationId;
    private String reservationCode;
    private String reservationStatus;
    private LocalDateTime reservationStart;
    private String ticketCode;
    private Boolean ticketUsed;
    private String vehicleId;
    private String vehiclePlateNumber;
    private String driverId;
    private String driverUsername;
}
