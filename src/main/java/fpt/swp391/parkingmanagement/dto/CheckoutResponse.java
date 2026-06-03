package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CheckoutResponse {
    private String sessionId;
    private LocalDateTime checkoutTime;
    private BigDecimal totalFee;
    private String paymentId;
}
