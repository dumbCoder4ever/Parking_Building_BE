package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlotOccupancyDetailResponse {

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

    private String driverId;
    private String driverUsername;
    private String driverFullName;
    private String driverEmail;
    private String driverPhoneNumber;

    private String guestName;
    private String guestPhone;

    private String vehicleId;
    private String vehiclePlateNumber;
    private String vehicleBrand;
    private String vehicleModel;
    private String vehicleColor;
    private String vehicleTypeName;

    private String reservationId;
    private String reservationCode;
    private String reservationStatus;
    private LocalDateTime reservationStart;
    private LocalDateTime reservationEnd;

    private String ticketCode;

    private String sessionId;
    private String sessionStatus;
    private LocalDateTime checkinTime;
    private LocalDateTime checkoutTime;
    private Long parkedDurationMinutes;
    private String checkinImageUrl;
}
