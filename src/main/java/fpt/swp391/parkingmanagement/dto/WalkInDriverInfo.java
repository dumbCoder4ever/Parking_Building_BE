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
@Schema(description = "Thông tin xe + driver cho trường hợp lookup walk-in driver (không có reservation).")
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

    // Pricing info
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal estimatedFee;

    // Session info
    private String ticketCode;
    private String sessionId;
    private LocalDateTime checkinTime;
    private String sessionStatus;
    /** Tổng thời gian đỗ (phút). */
    private Integer parkingDuration;
    /** URL ảnh xe lúc check-in (de staff xac minh). */
    private String checkinVehicleImage;
    /** URL ảnh xe lúc check-out (neu da check-out). */
    private String checkoutVehicleImage;

    // ── Location hierarchy (for staff checkout screen display) ─────────────
    /** Slot id đang giữ xe (null nếu lookup pre-checkin chưa có session). */
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
