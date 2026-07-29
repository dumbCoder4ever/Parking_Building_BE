package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CheckoutResponse {
    private String sessionId;
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private String zoneId;
    private String zoneName;
    private String slotId;
    private String slotName;
    private LocalDateTime checkinTime;
    private LocalDateTime checkoutTime;
    private BigDecimal totalFee;
    private String paymentId;
    
    private String vehicleTypeId;
    private String vehicleTypeName;
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    
    /** Tổng thời gian đỗ (phút), từ check-in đến check-out. */
    private Integer parkingDuration;
    private int parkingHours;
    private int parkingMinutes;
    
    private String sessionStatus;
    private String paymentStatus;
    private String checkoutVehicleImage;

    private BigDecimal estimatedFee;
    private String plateNumber;
    private String vehicleBrand;
    private String vehicleModel;
    private String vehicleColor;
    private String driverFullName;
    private String driverPhone;
    private String driverEmail;
}
