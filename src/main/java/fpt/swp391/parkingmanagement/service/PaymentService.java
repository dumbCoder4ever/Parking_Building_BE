package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.PaymentConfirmationDTO;
import fpt.swp391.parkingmanagement.dto.PaymentRequestDTO;
import fpt.swp391.parkingmanagement.dto.PaymentResponse;
import fpt.swp391.parkingmanagement.dto.PaymentResponseDTO;
import fpt.swp391.parkingmanagement.dto.StaffPaymentListItemResponse;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.Payment;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.enums.ConfirmationStatus;
import fpt.swp391.parkingmanagement.enums.EnumParser;
import fpt.swp391.parkingmanagement.enums.PaidStatusFilter;
import fpt.swp391.parkingmanagement.enums.PaymentStatus;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.repository.PaymentRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Autowired
    private VnPayService vnPayService;

    @Autowired
    private PayOSService payOSService;

    /**
     * STEP 1: STAFF initiates checkout for DRIVER
     * Staff generates transaction and sends payment options to driver
     */
    @Transactional
    public PaymentResponseDTO initiatePayment(PaymentRequestDTO paymentRequest) {
        ParkingSession session = parkingSessionRepository.findById(paymentRequest.getSessionId())
                .orElseThrow(() -> new RuntimeException("Parking session not found"));

        if (!session.getSessionStatus().equals("ACTIVE")) {
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

        String clientIp = "127.0.0.1";
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest httpReq = attrs.getRequest();
                String forwarded = httpReq.getHeader("X-FORWARDED-FOR");
                clientIp = (forwarded != null) ? forwarded : httpReq.getRemoteAddr();
            }
        } catch (Exception ignored) {}

        String paymentUrl = null;
        if ("VNPAY".equals(paymentRequest.getPaymentMethod())) {
            paymentUrl = vnPayService.createPaymentUrl(
                    savedPayment.getPaymentId(),
                    paymentRequest.getAmount(),
                    clientIp,
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
                .build();

        User driver = userRepository.findById(paymentRequest.getDriverId())
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        notificationService.sendPaymentInitiationToDriver(driver, response);

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
        parkingSessionRepository.save(session);

        return PaymentResponseDTO.builder()
                .paymentId(updatedPayment.getPaymentId())
                .sessionId(payment.getSession().getSessionId())
                .paymentMethod(updatedPayment.getPaymentMethod())
                .amount(updatedPayment.getAmount())
                .paymentStatus(PaymentStatus.PAID)
                .transactionCode(updatedPayment.getTransactionCode())
                .paymentTime(updatedPayment.getPaymentTime())
                .message("Payment successful. Waiting for staff confirmation.")
                .build();
    }

    /**
     * STEP 3: Staff confirms payment after gateway marked it PAID.
     */
    @Transactional
    public PaymentConfirmationDTO confirmPaymentByStaff(PaymentConfirmationDTO confirmationRequest) {
        Payment payment = paymentRepository.findById(confirmationRequest.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        ParkingSession session = parkingSessionRepository.findById(payment.getSession().getSessionId())
                .orElseThrow(() -> new RuntimeException("Parking session not found"));

        User driver = userRepository.findById(confirmationRequest.getDriverId())
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        PaymentConfirmationDTO confirmation = PaymentConfirmationDTO.builder()
                .paymentId(confirmationRequest.getPaymentId())
                .sessionId(payment.getSession().getSessionId())
                .driverId(confirmationRequest.getDriverId())
                .staffId(confirmationRequest.getStaffId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .transactionCode(payment.getTransactionCode())
                .confirmedAt(LocalDateTime.now())
                .build();

        if (confirmationRequest.getIsConfirmed()) {
            String currentStatus = payment.getPaymentStatus();

            if ("CONFIRMED".equalsIgnoreCase(currentStatus)) {
                throw new BaseAPIException(ErrorCode.PAYMENT_ALREADY_CONFIRMED,
                        "Payment has already been confirmed by staff");
            }

            // Session PAID but payment record still PENDING (webhook/return chưa cập nhật payment)
            if ("PENDING".equalsIgnoreCase(currentStatus)
                    && "PAID".equalsIgnoreCase(session.getPaymentStatus())) {
                payment.setPaymentStatus("PAID");
                currentStatus = "PAID";
            }

            if (currentStatus == null
                    || (!"PAID".equalsIgnoreCase(currentStatus) && !"SUCCESS".equalsIgnoreCase(currentStatus))) {
                throw new BaseAPIException(ErrorCode.PAYMENT_NOT_COMPLETED,
                        "Payment must be PAID before staff can confirm. "
                                + "Current paymentStatus=" + currentStatus
                                + ", sessionPaymentStatus=" + session.getPaymentStatus());
            }

            payment.setPaymentStatus("CONFIRMED");
            session.setPaymentStatus("PAID");
            confirmation.setConfirmationStatus(ConfirmationStatus.CONFIRMED);
            confirmation.setMessage("Payment confirmed successfully. You may exit.");
        } else {
            payment.setPaymentStatus("FAILED");
            session.setPaymentStatus("FAILED");
            confirmation.setConfirmationStatus(ConfirmationStatus.FAILED);
            confirmation.setReason(confirmationRequest.getReason());
            confirmation.setMessage("Payment confirmation failed: " + confirmationRequest.getReason());
        }

        paymentRepository.save(payment);
        parkingSessionRepository.save(session);

        notificationService.sendPaymentConfirmationToDriver(driver, confirmation);

        return confirmation;
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
}
