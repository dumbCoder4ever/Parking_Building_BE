package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ReservationResponse {
    private String reservationId;
    private String reservationCode;
    private String reservationStatus;
    private String reservationNote;
    private LocalDateTime reservationStart;
    private LocalDateTime createdAt;

    // User info
    private String userId;
    private String username;

    // Vehicle info
    private String vehicleId;
    private String vehiclePlate;
    private String vehicleColor;
    private String vehicleBrand;
    private String vehicleModel;
    private String vehicleImageUrl;

    // Slot info
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

    // Ticket info
    private String ticketCode;

    // Pricing info
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private Integer maxHours;
    private String vehicleTypeName;

    // Parking session info
    private String sessionId;
    private LocalDateTime checkinTime;
    private LocalDateTime checkoutTime;
    private BigDecimal totalFee;
    private String checkinImageUrl;
    private String checkoutImageUrl;
    /** Tổng thời gian đỗ (phút). */
    private Integer parkingDuration;
    private String paymentStatus;
}
