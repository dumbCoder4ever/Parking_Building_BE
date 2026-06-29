package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GuestCheckoutRequest {

    @NotBlank(message = "Ticket code is required")
    private String ticketCode;

    private String paymentMethod;

    /** Set programmatically after Cloudinary upload */
    private String checkoutImageUrl;
}
