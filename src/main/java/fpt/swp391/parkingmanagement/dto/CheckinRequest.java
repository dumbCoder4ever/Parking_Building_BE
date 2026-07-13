package fpt.swp391.parkingmanagement.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Unified check-in request — gộp luồng manual cũ và quick-checkin.
 *
 * <p>Staff chỉ cần upload ảnh biển số + khai báo building đang làm việc.
 * Backend tự OCR, tự phân biệt Driver/Guest, tự chống duplicate.</p>
 *
 * <p>Flow (theo script §3):</p>
 * <ol>
 *   <li>OCR → plateNumber</li>
 *   <li>Check duplicate ACTIVE/PENDING_PAYMENT theo biển số</li>
 *   <li>Tìm reservation PENDING/APPROVED theo plate + building → DRIVER</li>
 *   <li>Không có reservation → GUEST (cần vehicleTypeId để auto-assign slot)</li>
 * </ol>
 */
@Data
@Schema(description = "Unified checkin: staff upload ảnh biển số + buildingId. BE tự OCR, tự quyết Driver/Guest, tự chống duplicate.")
public class CheckinRequest {

    @Schema(description = "Ảnh biển số xe. BẮT BUỘC — backend dùng OCR để nhận diện biển số.", requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile plateImage;

    @Schema(description = "Building ID nơi staff đang làm việc. BẮT BUỘC.", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "buildingId is required")
    private String buildingId;

    @Schema(description = "Loại xe cho Guest checkin. BẮT BUỘC cho luồng Guest (BE tự quyết — sẽ validate khi cần).")
    private String vehicleTypeId;

    @Schema(description = "Optional — màu xe (lưu vào Vehicle nếu là Guest).")
    private String vehicleColor;

    @Schema(description = "Optional — hãng xe.")
    private String brand;

    @Schema(description = "Optional — model xe.")
    private String model;

    @Schema(description = "Optional — tên khách (chỉ áp dụng cho Guest).")
    private String guestName;

    @Schema(description = "Optional — SĐT khách (chỉ áp dụng cho Guest).")
    private String guestPhone;

    @Schema(description = "Optional — ghi chú thêm.")
    private String note;
}
