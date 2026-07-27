package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.PaymentRequestDTO;
import fpt.swp391.parkingmanagement.dto.PaymentResponse;
import fpt.swp391.parkingmanagement.dto.PaymentResponseDTO;
import fpt.swp391.parkingmanagement.dto.StaffPaymentListItemResponse;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.Payment;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.enums.EnumParser;
import fpt.swp391.parkingmanagement.enums.PaidStatusFilter;
import fpt.swp391.parkingmanagement.enums.PaymentStatus;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.entity.Ticket;
import fpt.swp391.parkingmanagement.repository.PaymentRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    @Autowired
    private VnPayService vnPayService;

    @Autowired
    private PayOSService payOSService;

    /**
     * STEP 1: STAFF initiates checkout for DRIVER
     * Staff generates transaction and sends payment options to driver
     */
    @Transactional
    public PaymentResponseDTO initiatePayment(PaymentRequestDTO paymentRequest, String clientIp) {
        ParkingSession session;
        if (paymentRequest.getTicketCode() != null && !paymentRequest.getTicketCode().isBlank()) {
            Ticket ticket = ticketRepository.findByTicketCode(paymentRequest.getTicketCode())
                    .orElseThrow(() -> new RuntimeException("Ticket not found: " + paymentRequest.getTicketCode()));
            // Accept both PENDING_PAYMENT (after checkin) and ACTIVE (already paid once, re-initiate case)
            session = parkingSessionRepository
                    .findByTicketTicketIdAndSessionStatusIn(ticket.getTicketId(),
                            java.util.List.of("ACTIVE", "PENDING_PAYMENT"))
                    .orElseThrow(() -> new RuntimeException("No active session for ticket: " + paymentRequest.getTicketCode()));
        } else {
            session = parkingSessionRepository.findById(paymentRequest.getSessionId())
                    .orElseThrow(() -> new RuntimeException("Parking session not found"));
        }

        // Checkin sets session to PENDING_PAYMENT; payment is initiated from that state and the
        // webhook later transitions to ACTIVE. Accept both to match the real flow.
        String currentStatus = session.getSessionStatus();
        if (!"ACTIVE".equalsIgnoreCase(currentStatus) && !"PENDING_PAYMENT".equalsIgnoreCase(currentStatus)) {
            throw new RuntimeException("Session is not active");
        }

        Payment payment = new Payment();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setAmount(paymentRequest.getAmount());
        payment.setPaymentStatus("PENDING");
        payment.setPaymentMethod(paymentRequest.getPaymentMethod());
        payment.setPaymentTime(LocalDateTime.now());
        payment.setTransactionCode(generateTransactionCode());
        payment.setCreatedAt(LocalDateTime.now());
        payment.setSession(session);
        payment.setNote(paymentRequest.getNote());

        Payment savedPayment = paymentRepository.save(payment);

        String resolvedClientIp = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";

        String paymentUrl = null;
        if ("VNPAY".equals(paymentRequest.getPaymentMethod())) {
            paymentUrl = vnPayService.createPaymentUrl(
                    savedPayment.getPaymentId(),
                    paymentRequest.getAmount(),
                    resolvedClientIp,
                    paymentRequest.getBankCode(),
                    paymentRequest.getLanguage()
            );
        } else if ("PAYOS".equals(paymentRequest.getPaymentMethod())) {
            CreatePaymentLinkResponse payosResponse = payOSService.createPaymentLink(
                    savedPayment.getPaymentId(),
                    paymentRequest.getAmount()
            );
            savedPayment.setTransactionCode(String.valueOf(payosResponse.getOrderCode()));
            paymentRepository.save(savedPayment);
            paymentUrl = payosResponse.getCheckoutUrl();
        }

        // driverId is optional: null or blank means this is a guest session
        User driver = (paymentRequest.getDriverId() != null && !paymentRequest.getDriverId().isBlank())
                ? userRepository.findById(paymentRequest.getDriverId())
                        .orElseThrow(() -> new RuntimeException("Driver not found"))
                : null;

        String displayName = (driver != null)
                ? driver.getFullName()
                : session.getGuestName();

        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .paymentId(savedPayment.getPaymentId())
                .sessionId(session.getSessionId())
                .paymentMethod(paymentRequest.getPaymentMethod())
                .amount(paymentRequest.getAmount())
                .paymentStatus(PaymentStatus.PENDING)
                .transactionCode(savedPayment.getTransactionCode())
                .paymentTime(LocalDateTime.now())
                .paymentUrl(paymentUrl)
                .message("Payment initiated. Driver can now proceed with payment.")
                .driverName(displayName)
                .build();

        return response;
    }

    /**
     * STEP 2: Driver completes payment (gateway webhook) -> status becomes PAID automatically.
     */
    @Transactional
    public PaymentResponseDTO confirmPaymentSuccess(String paymentId, String transactionCode) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setPaymentStatus("PAID");
        payment.setTransactionCode(transactionCode);
        payment.setPaymentTime(LocalDateTime.now());

        Payment updatedPayment = paymentRepository.save(payment);

        ParkingSession session = parkingSessionRepository.findById(payment.getSession().getSessionId())
                .orElseThrow(() -> new RuntimeException("Parking session not found"));
        session.setPaymentStatus("PAID");
        // Move session from PENDING_PAYMENT to ACTIVE once payment is confirmed.
        if ("PENDING_PAYMENT".equalsIgnoreCase(session.getSessionStatus())) {
            session.setSessionStatus("ACTIVE");
        }
        parkingSessionRepository.save(session);

        User driver = session.getReservation() != null ? session.getReservation().getUser() : null;
        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .paymentId(updatedPayment.getPaymentId())
                .sessionId(payment.getSession().getSessionId())
                .paymentMethod(updatedPayment.getPaymentMethod())
                .amount(updatedPayment.getAmount())
                .paymentStatus(PaymentStatus.PAID)
                .transactionCode(updatedPayment.getTransactionCode())
                .paymentTime(updatedPayment.getPaymentTime())
                .message("Payment successful. Staff may proceed with checkout.")
                .build();

        auditLogService.record(
                "PAYMENT_PAID",
                "PAYMENT",
                updatedPayment.getPaymentId(),
                resolveBuildingId(session),
                "PENDING",
                "PAID",
                "Payment confirmed " + updatedPayment.getTransactionCode(),
                null);

        return response;
    }

    /**
     * Handle payment failure from gateway
     */
    @Transactional
    public PaymentResponseDTO handlePaymentFailure(String paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setPaymentStatus("FAILED");
        Payment updatedPayment = paymentRepository.save(payment);

        ParkingSession session = parkingSessionRepository.findById(payment.getSession().getSessionId())
                .orElseThrow(() -> new RuntimeException("Parking session not found"));
        session.setPaymentStatus("FAILED");
        parkingSessionRepository.save(session);

        // Publish PAYMENT_FAILED notification
        // Notification module removed — see docs/REMOVED_NOTIFICATIONS.md.
        // (Original block was a try/catch that published PAYMENT_FAILED via notificationPublisher.)

        auditLogService.record(
                "PAYMENT_FAILED",
                "PAYMENT",
                updatedPayment.getPaymentId(),
                resolveBuildingId(session),
                "PENDING",
                "FAILED",
                "Payment failed: " + reason,
                null);

        return PaymentResponseDTO.builder()
                .paymentId(updatedPayment.getPaymentId())
                .sessionId(payment.getSession().getSessionId())
                .paymentMethod(updatedPayment.getPaymentMethod())
                .amount(updatedPayment.getAmount())
                .paymentStatus(PaymentStatus.FAILED)
                .transactionCode(updatedPayment.getTransactionCode())
                .message("Payment failed: " + reason)
                .build();
    }

    private String generateTransactionCode() {
        return "TXN-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    public PaymentResponseDTO getPaymentDetails(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<StaffPaymentListItemResponse> getAllPaymentsForStaff(PaidStatusFilter paidStatus, int limit) {
        String normalizedStatus = paidStatus != null ? paidStatus.name() : null;
        return paymentRepository.findAllByPaidStatus(normalizedStatus, PageRequest.of(0, limit))
                .stream()
                .map(StaffPaymentListItemResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentHistoryByDriverId(String driverId, String requesterEmail, int limit) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        boolean isStaffOrAdmin = requester.getRole() != null
                && (requester.getRole().toUpperCase().contains("STAFF")
                || requester.getRole().toUpperCase().contains("MANAGER")
                || requester.getRole().toUpperCase().contains("ADMIN"));

        if (!isStaffOrAdmin && !driverId.equals(requester.getUserId())) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED);
        }

        return paymentRepository.findByDriverIdOrderByPaymentTimeDesc(driverId, PageRequest.of(0, limit))
                .stream()
                .map(PaymentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public PaymentResponseDTO getPaymentByOrderCode(long orderCode) {
        Payment payment = paymentRepository.findByTransactionCode(String.valueOf(orderCode))
                .orElseThrow(() -> new RuntimeException("Payment not found for orderCode: " + orderCode));

        return toPaymentResponse(payment);
    }

    private PaymentResponseDTO toPaymentResponse(Payment payment) {
        return PaymentResponseDTO.builder()
                .paymentId(payment.getPaymentId())
                .sessionId(payment.getSession().getSessionId())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .paymentStatus(EnumParser.parsePaymentStatus(payment.getPaymentStatus()))
                .transactionCode(payment.getTransactionCode())
                .paymentTime(payment.getPaymentTime())
                .build();
    }

    private String resolveBuildingId(ParkingSession session) {
        try {
            if (session == null || session.getSlot() == null) {
                return null;
            }
            var zone = session.getSlot().getZone();
            if (zone == null || zone.getFloor() == null || zone.getFloor().getBuilding() == null) {
                return null;
            }
            return zone.getFloor().getBuilding().getBuildingId();
        } catch (Exception e) {
            return null;
        }
    }
}
