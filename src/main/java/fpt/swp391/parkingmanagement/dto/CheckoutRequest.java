package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "Ticket code is required")
    private String ticketCode;

    private String paymentMethod;
    // Set programmatically after Cloudinary upload — not sent by client
    private String checkoutVehicleImage;
}
