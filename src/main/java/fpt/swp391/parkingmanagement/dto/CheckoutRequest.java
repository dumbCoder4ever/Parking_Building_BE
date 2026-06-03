package fpt.swp391.parkingmanagement.dto;

import lombok.Data;

@Data
public class CheckoutRequest {
    private String ticketCode;
    private String paymentMethod; // CASH, BANKING, ...
}
