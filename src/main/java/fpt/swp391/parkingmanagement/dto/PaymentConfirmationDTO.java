package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentConfirmationDTO {
    private String paymentId;
    private String sessionId;
    private String driverId;
    private String staffId;
    private BigDecimal amount;
    private String paymentMethod;
    private String transactionCode;
    private Boolean isConfirmed;
    private String confirmationStatus; // SUCCESS or FAILED
    private String reason; // Reason for failure if any
    private String message;
    private LocalDateTime confirmedAt;
    private String note;
}
