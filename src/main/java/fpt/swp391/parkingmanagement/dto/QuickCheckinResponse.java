package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "Quick checkin response. Contains the newly created session and OCR-detected plate number.")
public class QuickCheckinResponse {

    @Schema(description = "Checkin type: DRIVER | DRIVER_WALK_IN | GUEST")
    private String checkinType;

    private String ticketCode;
    private String sessionId;
    private String plateNumber;
    private Double ocrConfidence;
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
    private String checkinVehicleImage;
    private Integer parkingDuration;
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal estimatedFee;
    private PlateDuplicateInfo duplicateActiveSession;
    private String driverUserId;
    private String driverUsername;
    private String driverFullName;
    private String driverPhone;
    private String driverEmail;
}
