package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "Guest checkout via OCR: staff scans a license plate image, the system detects the plate, finds the active session, and checks out. Default payment method is CASH.")
public class GuestCheckoutOcrRequest {

    @Schema(description = "License plate image at exit. Required.")
    private MultipartFile plateImage;

    @Schema(description = "Ticket code (ticketCode). Used to confirm the correct session. Required.")
    @NotBlank(message = "ticketCode is required")
    private String ticketCode;

    @Schema(description = "Payment method: CASH (default), VNPAY, PAYOS, MOMO.")
    private String paymentMethod;

    @Schema(description = "Check-out vehicle image (optional).")
    private MultipartFile checkoutImage;

    /** Set programmatically after Cloudinary upload */
    private String checkoutVehicleImage;
}
