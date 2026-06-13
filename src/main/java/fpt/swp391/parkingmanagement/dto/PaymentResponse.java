package fpt.swp391.parkingmanagement.dto;

import fpt.swp391.parkingmanagement.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String paymentId;
    private String sessionId;
    private String reservationCode;
    private String paymentMethod;
    private BigDecimal amount;
    private LocalDateTime paymentTime;
    private String paymentStatus;
    private String paidStatus;
    private String transactionCode;
    private String note;
    private LocalDateTime createdAt;

    public static String resolvePaidStatus(String paymentStatus) {
        if (paymentStatus == null) {
            return "UNPAID";
        }
        String normalized = paymentStatus.trim().toUpperCase();
        if ("PAID".equals(normalized) || "CONFIRMED".equals(normalized) || "SUCCESS".equals(normalized)) {
            return "PAID";
        }
        return "UNPAID";
    }

    public static PaymentResponse fromEntity(Payment payment) {
        String reservationCode = null;
        if (payment.getSession() != null && payment.getSession().getReservation() != null) {
            reservationCode = payment.getSession().getReservation().getReservationCode();
        }

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .sessionId(payment.getSession() != null ? payment.getSession().getSessionId() : null)
                .reservationCode(reservationCode)
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .paymentTime(payment.getPaymentTime())
                .paymentStatus(payment.getPaymentStatus())
                .paidStatus(resolvePaidStatus(payment.getPaymentStatus()))
                .transactionCode(payment.getTransactionCode())
                .note(payment.getNote())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
