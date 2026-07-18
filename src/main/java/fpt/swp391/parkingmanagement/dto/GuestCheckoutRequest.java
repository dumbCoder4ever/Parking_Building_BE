package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GuestCheckoutRequest {

    /** Set programmatically from OCR result */
    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    private String paymentMethod;

    /** Set programmatically after Cloudinary upload */
    private String checkoutVehicleImage;
}
