package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "Response cho quick checkin. Chứa thông tin session vừa tạo cùng biển số đã OCR.")
public class QuickCheckinResponse {

    @Schema(description = "Loại checkin: DRIVER (có reservation) hoặc GUEST (vãng lai).")
    private String checkinType;

    @Schema(description = "Mã vé để checkout. Driver: mã TKT-xxx, Guest: mã G-xxx.")
    private String ticketCode;

    private String sessionId;
    private String plateNumber;
    private Double ocrConfidence;
    private String vehicleColor;
    private String brand;
    private String model;

    @Schema(description = "ID loại xe (MOTORCYCLE, CAR...).")
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

    @Schema(description = "Thời gian đỗ (phút). Check-in mới = 0.")
    private Integer parkingDuration;

    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal estimatedFee;

    @Schema(description = "Thông tin cảnh báo nếu biển số đang có session ACTIVE khác (chỉ khi checkin driver).")
    private PlateDuplicateInfo duplicateActiveSession;

    @Schema(description = "User ID của driver (chỉ có khi checkinType = DRIVER hoặc DRIVER_WALK_IN).")
    private String driverUserId;

    @Schema(description = "Username của driver.")
    private String driverUsername;

    @Schema(description = "Họ tên driver.")
    private String driverFullName;

    @Schema(description = "Số điện thoại driver.")
    private String driverPhone;

    @Schema(description = "Email driver.")
    private String driverEmail;
}
