package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class GuestCheckinResponse {
    private String sessionId;

    private String guestName;
    private String guestPhone;

    private String vehiclePlate;
    private String vehicleColor;
    private String brand;
    private String model;
    private String vehicleTypeId;
    private String vehicleTypeName;

    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private String zoneId;
    private String zoneName;
    private String slotId;
    private String slotName;

    private LocalDateTime checkinTime;
    private String checkinImageUrl;

    private BigDecimal estimatedFee;
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
}
