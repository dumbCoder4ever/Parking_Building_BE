package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "Quick checkin: staff chỉ cần quét ảnh biển số. Hệ thống tự nhận diện biển số, tìm reservation (driver) hoặc auto-assign slot (guest), rồi tạo session.")
public class QuickCheckinRequest {

    @Schema(description = "Ảnh biển số xe. Staff chụp ảnh biển số → upload lên. BẮT BUỘC. Hệ thống OCR tự nhận diện biển số, tìm reservation (driver) hoặc auto-assign slot (guest), rồi tạo session.")
    private MultipartFile plateImage;

    @Schema(description = "Building ID nơi staff đang làm việc. BẮT BUỘC. Hệ thống dùng để auto-assign slot (guest) và validate reservation (driver).")
    @NotBlank(message = "buildingId is required")
    private String buildingId;

    @Schema(description = "Loại xe cho guest checkin. BẮT BUỘC nếu mode=GUEST, không cần nếu mode=DRIVER.")
    private String vehicleTypeId;

    @Schema(description = "Chế độ checkin: DRIVER = tìm reservation theo biển số (mặc định), GUEST = tạo session vãng lai (auto-assign slot). Mặc định: DRIVER.")
    private QuickMode mode = QuickMode.DRIVER;

    public enum QuickMode {
        @Schema(description = "Tự động tìm reservation PENDING/APPROVED theo biển số. Nếu không có reservation → trả lỗi.")
        DRIVER,
        @Schema(description = "Tạo session vãng lai. Hệ thống tự assign slot trống theo buildingId + vehicleTypeId. Nếu không có slot → trả lỗi.")
        GUEST
    }
}
