package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Vehicle + driver info for walk-in driver checkout lookup.")
public class WalkInDriverInfo {

    private String vehicleId;
    private String userId;
    private String plateNumber;
    private String brand;
    private String model;
    private String vehicleColor;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private String driverFullName;
    private String driverPhone;
    private String driverEmail;

    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal estimatedFee;

    private String ticketCode;
    private String sessionId;
    private LocalDateTime checkinTime;
    private String sessionStatus;
    private Integer parkingDuration;
    private String checkinVehicleImage;
    private String checkoutVehicleImage;

    private String slotId;
    private String slotName;
    private String slotStatus;
    private String zoneId;
    private String zoneName;
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private String buildingId;
    private String buildingName;
    private String buildingAddress;
}
