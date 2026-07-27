package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "Quick checkin response. Contains the newly created session and OCR-detected plate number.")
public class QuickCheckinResponse {

    @Schema(description = "Checkin type: DRIVER (with reservation) or GUEST (walk-in).")
    private String checkinType;

    @Schema(description = "Ticket code for checkout. Driver: TKT-xxx, Guest: G-xxx.")
    private String ticketCode;

    private String sessionId;
    private String plateNumber;
    private Double ocrConfidence;
    private String vehicleColor;
    private String brand;
    private String model;

    @Schema(description = "Vehicle type ID (MOTORCYCLE, CAR, etc.).")
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

    @Schema(description = "Parking duration in minutes. New check-in = 0.")
    private Integer parkingDuration;

    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal estimatedFee;

    @Schema(description = "Warning if the plate already has another ACTIVE session (driver checkin only).")
    private PlateDuplicateInfo duplicateActiveSession;
}
