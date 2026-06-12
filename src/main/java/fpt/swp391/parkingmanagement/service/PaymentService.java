package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.PaymentRequestDTO;
import fpt.swp391.parkingmanagement.dto.PaymentResponseDTO;
import fpt.swp391.parkingmanagement.dto.PaymentConfirmationDTO;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.Payment;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.enums.PaymentStatus;
import fpt.swp391.parkingmanagement.enums.SessionStatus;
import fpt.swp391.parkingmanagement.repository.PaymentRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {
    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ParkingSessionRepository parkingSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

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
        // Validate session exists and is active
        ParkingSession session = parkingSessionRepository.findById(paymentRequest.getSessionId())
                .orElseThrow(() -> new RuntimeException("Parking session not found"));

        if (!session.getSessionStatus().equals("ACTIVE")) {
            throw new RuntimeException("Session is not active");
        }

        // Create payment record with PENDING status
//        Payment payment = Payment.builder()
//                .paymentId(UUID.randomUUID().toString())
//                .sessionId(session.getSessionId())
//                .paymentMethod(paymentRequest.getPaymentMethod())
//                .amount(paymentRequest.getAmount())
//                .paymentStatus(PaymentStatus.PENDING)
//                .transactionCode(generateTransactionCode())
//                .note(paymentRequest.getNote())
//                .createdAt(LocalDateTime.now())
//                .build();

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

        // Lấy IP của request hiện tại
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
        String qrCode = null;
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
        } else if ("MOMO".equals(paymentRequest.getPaymentMethod())) {
            qrCode = generateQRCode(savedPayment.getPaymentId(), paymentRequest.getAmount());
        }

        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .paymentId(savedPayment.getPaymentId())
                .sessionId(session.getSessionId())
                .paymentMethod(paymentRequest.getPaymentMethod())
                .amount(paymentRequest.getAmount())
                .paymentStatus("PENDING")
                .transactionCode(savedPayment.getTransactionCode())
                .paymentTime(LocalDateTime.now())
                .qrCode(qrCode)
                .paymentUrl(paymentUrl)
                .message("Payment initiated. Driver can now proceed with payment.")
                .build();

        // STEP 2: Send payment details to DRIVER
        User driver = userRepository.findById(paymentRequest.getDriverId())
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        notificationService.sendPaymentInitiationToDriver(driver, response);

        return response;
    }

    /**
     * STEP 3: DRIVER makes payment and system confirms it
     */
    @Transactional
    public PaymentResponseDTO confirmPaymentSuccess(String paymentId, String transactionCode) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setPaymentStatus("SUCCESS");
        payment.setTransactionCode(transactionCode);
        payment.setPaymentTime(LocalDateTime.now());

        Payment updatedPayment = paymentRepository.save(payment);

        // Update parking session payment status
        ParkingSession session = parkingSessionRepository.findById(payment.getSession().getSessionId())
                .orElseThrow(() -> new RuntimeException("Parking session not found"));
        session.setPaymentStatus(String.valueOf(PaymentStatus.PAID));
        parkingSessionRepository.save(session);

        return PaymentResponseDTO.builder()
                .paymentId(updatedPayment.getPaymentId())
                .sessionId(payment.getSession().getSessionId())
                .paymentMethod(updatedPayment.getPaymentMethod())
                .amount(updatedPayment.getAmount())
                .paymentStatus("PAID")
                .transactionCode(updatedPayment.getTransactionCode())
                .paymentTime(updatedPayment.getPaymentTime())
                .message("Payment successful. Waiting for staff confirmation.")
                .build();
    }

    /**
     * STEP 4: STAFF confirms payment and sends final confirmation to DRIVER
     */
    @Transactional
    public PaymentConfirmationDTO confirmPaymentByStaff(PaymentConfirmationDTO confirmationRequest) {
        Payment payment = paymentRepository.findById(confirmationRequest.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        ParkingSession session = parkingSessionRepository.findById(payment.getSession().getSessionId())
                .orElseThrow(() -> new RuntimeException("Parking session not found"));

        User driver = userRepository.findById(confirmationRequest.getDriverId())
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        User staff = userRepository.findById(confirmationRequest.getStaffId())
                .orElseThrow(() -> new RuntimeException("Staff not found"));

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
            payment.setPaymentStatus("SUCCESS");
            session.setPaymentStatus("PAID");
            //session.setSessionStatus("COMPLETED"); Xóa vì conflict checkout
            confirmation.setConfirmationStatus("SUCCESS");
            confirmation.setMessage("Payment confirmed successfully. You may exit.");
        } else {
            payment.setPaymentStatus("FAILED");
            session.setPaymentStatus("FAILED");
            confirmation.setConfirmationStatus("FAILED");
            confirmation.setReason(confirmationRequest.getReason());
            confirmation.setMessage("Payment confirmation failed: " + confirmationRequest.getReason());
        }

        paymentRepository.save(payment);
        parkingSessionRepository.save(session);

        // STEP 5: Send final confirmation back to DRIVER
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
                .paymentStatus("FAILED")
                .transactionCode(updatedPayment.getTransactionCode())
                .message("Payment failed: " + reason)
                .build();
    }

    private String generateTransactionCode() {
        return "TXN-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    private String generateQRCode(String paymentId, BigDecimal amount) {
        return "QR_" + paymentId + "_" + amount;
    }

    public PaymentResponseDTO getPaymentDetails(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        return toPaymentResponse(payment);
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
                .paymentStatus(payment.getPaymentStatus())
                .transactionCode(payment.getTransactionCode())
                .paymentTime(payment.getPaymentTime())
                .build();
    }
}
