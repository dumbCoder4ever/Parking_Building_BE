package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "Ticket code is required")
    private String ticketCode;

    private String paymentMethod;
    private String checkoutImageUrl;
}
