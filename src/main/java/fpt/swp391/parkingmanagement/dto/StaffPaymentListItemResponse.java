package fpt.swp391.parkingmanagement.dto;

import fpt.swp391.parkingmanagement.entity.Payment;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.User;
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
public class StaffPaymentListItemResponse {

    private String paymentId;
    private String sessionId;
    private String driverId;
    private String driverName;
    private String driverEmail;
    private String ticketCode;
    private String reservationCode;
    private String paymentMethod;
    private BigDecimal amount;
    private String paymentStatus;
    private String paidStatus;
    private String transactionCode;
    private LocalDateTime paymentTime;
    private LocalDateTime createdAt;
    private String sessionPaymentStatus;
    private String note;

    public static StaffPaymentListItemResponse fromEntity(Payment payment) {
        ParkingSession session = payment.getSession();
        Reservation reservation = session != null ? session.getReservation() : null;
        User driver = reservation != null ? reservation.getUser() : null;

        String ticketCode = null;
        if (session != null && session.getTicket() != null) {
            ticketCode = session.getTicket().getTicketCode();
        }

        return StaffPaymentListItemResponse.builder()
                .paymentId(payment.getPaymentId())
                .sessionId(session != null ? session.getSessionId() : null)
                .driverId(driver != null ? driver.getUserId() : null)
                .driverName(driver != null ? driver.getFullName() : null)
                .driverEmail(driver != null ? driver.getEmail() : null)
                .ticketCode(ticketCode)
                .reservationCode(reservation != null ? reservation.getReservationCode() : null)
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .paymentStatus(payment.getPaymentStatus())
                .paidStatus(PaymentResponse.resolvePaidStatus(payment.getPaymentStatus()))
                .transactionCode(payment.getTransactionCode())
                .paymentTime(payment.getPaymentTime())
                .createdAt(payment.getCreatedAt())
                .sessionPaymentStatus(session != null ? session.getPaymentStatus() : null)
                .note(payment.getNote())
                .build();
    }
}
