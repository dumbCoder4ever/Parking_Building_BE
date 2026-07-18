package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "Guest checkout bằng OCR: staff quét ảnh biển số → hệ thống nhận diện → tìm session đang hoạt động → checkout. Thanh toán CASH mặc định.")
public class GuestCheckoutOcrRequest {

    @Schema(description = "Ảnh biển số xe lúc xe ra. Staff chụp ảnh biển số → upload lên. BẮT BUỘC.")
    private MultipartFile plateImage;

    @Schema(description = "Mã vé (ticketCode). Hệ thống dùng để xác nhận session chính xác. BẮT BUỘC.")
    @NotBlank(message = "ticketCode is required")
    private String ticketCode;

    @Schema(description = "Phương thức thanh toán: CASH (mặc định), VNPAY, PAYOS, MOMO.")
    private String paymentMethod;

    @Schema(description = "Ảnh check-out lúc xe ra (tùy chọn).")
    private MultipartFile checkoutImage;

    /** Set programmatically after Cloudinary upload */
    private String checkoutVehicleImage;
}
