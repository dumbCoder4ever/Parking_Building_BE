package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Unified check-in response — server quyết định {@code checkinType} (DRIVER hoặc GUEST)
 * và trả về đầy đủ thông tin để FE hiển thị kết quả.
 */
@Data
@Schema(description = "Unified checkin response. BE tự quyết Driver/Guest và trả về thông tin session vừa tạo.")
public class CheckinResponse {

    @Schema(description = "Loại checkin: DRIVER (có reservation) hoặc GUEST (vãng lai).", allowableValues = {"DRIVER", "GUEST"})
    private String checkinType;

    @Schema(description = "Mã vé để dùng cho checkout. Driver: mã TKT-xxx, Guest: mã G-xxx.")
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
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal estimatedFee;

    @Schema(description = "Trạng thái session sau checkin — luôn là PENDING_PAYMENT (sẽ chuyển ACTIVE sau khi payment webhook về).")
    private String sessionStatus;

    @Schema(description = "Trạng thái payment — luôn là UNPAID ngay sau checkin.")
    private String paymentStatus;
}
