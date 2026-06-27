package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GuestCheckoutRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    private String paymentMethod;

    /** Set programmatically after Cloudinary upload */
    private String checkoutImageUrl;
}
