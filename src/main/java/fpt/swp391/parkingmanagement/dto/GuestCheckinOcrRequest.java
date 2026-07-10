package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "Guest checkin bằng OCR: staff quét ảnh biển số xe → hệ thống nhận diện biển số → auto-assign slot → tạo session.")
public class GuestCheckinOcrRequest {

    @Schema(description = "Ảnh biển số xe. Staff chụp ảnh biển số xe khách → upload lên. BẮT BUỘC.")
    private MultipartFile plateImage;

    @Schema(description = "Building ID nơi staff đang làm việc. BẮT BUỘC.")
    @NotBlank(message = "buildingId is required")
    private String buildingId;

    @Schema(description = "Loại xe (MOTORCYCLE, CAR...). BẮT BUỘC.")
    @NotBlank(message = "vehicleTypeId is required")
    private String vehicleTypeId;

    @Schema(description = "Màu xe (tùy chọn).")
    private String vehicleColor;

    @Schema(description = "Hãng xe (tùy chọn).")
    private String brand;

    @Schema(description = "Dòng xe (tùy chọn).")
    private String model;

    @Schema(description = "Tên khách (tùy chọn).")
    private String guestName;

    @Schema(description = "SĐT khách (tùy chọn).")
    private String guestPhone;

    @Schema(description = "Ghi chú (tùy chọn).")
    private String note;

    @Schema(description = "Ảnh check-in lúc xe vào (tùy chọn). Upload ảnh → lưu URL tại đây.")
    private MultipartFile checkinImage;

    /** Set programmatically after Cloudinary upload */
    private String checkinImageUrl;
}
