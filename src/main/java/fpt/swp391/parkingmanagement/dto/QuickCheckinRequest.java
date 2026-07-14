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

    @Schema(description = "Loại xe cho guest checkin (khi không tìm thấy reservation). BẮT BUỘC nếu plate không có reservation.")
    private String vehicleTypeId;

    private String vehicleColor;
    private String guestName;
    private String guestPhone;
    private String note;

    /** Set programmatically after Cloudinary upload — not sent by client */
    private String checkinImageUrl;
}
