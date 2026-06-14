package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import fpt.swp391.parkingmanagement.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDTO {
    private String paymentId;
    private String sessionId;
    private String paymentMethod;
    private BigDecimal amount;
    private PaymentStatus paymentStatus;
    private String transactionCode;
    private LocalDateTime paymentTime;
    private String message;
    private String paymentUrl;
    private String driverName;
}
