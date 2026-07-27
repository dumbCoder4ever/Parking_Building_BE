package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.PaymentRequestDTO;
import fpt.swp391.parkingmanagement.dto.PaymentResponse;
import fpt.swp391.parkingmanagement.dto.PaymentResponseDTO;
import fpt.swp391.parkingmanagement.dto.StaffPaymentListItemResponse;
import fpt.swp391.parkingmanagement.enums.PaidStatusFilter;
import fpt.swp391.parkingmanagement.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Initiate payment for a parking session")
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> initiatePayment(
            @RequestBody PaymentRequestDTO paymentRequest,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        PaymentResponseDTO response = paymentService.initiatePayment(paymentRequest, clientIp);
        return ResponseEntity.ok(ApiResponse.ok("Payment initiated successfully", response));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-FORWARDED-FOR");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Operation(summary = "Confirm successful payment from gateway callback")
    @PostMapping("/confirm-success")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> confirmPaymentSuccess(
            @RequestParam String paymentId,
            @RequestParam String transactionCode) {
        PaymentResponseDTO response = paymentService.confirmPaymentSuccess(paymentId, transactionCode);
        return ResponseEntity.ok(ApiResponse.ok("Payment confirmed successfully", response));
    }

    @Operation(summary = "Handle payment failure from gateway")
    @PostMapping("/handle-failure")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> handlePaymentFailure(
            @RequestParam String paymentId,
            @RequestParam String reason) {
        PaymentResponseDTO response = paymentService.handlePaymentFailure(paymentId, reason);
        return ResponseEntity.ok(ApiResponse.ok("Payment failure recorded", response));
    }

    @Operation(summary = "Staff lists payments (filter: PAID or UNPAID)")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffPaymentListItemResponse>>> getAllPayments(
            @Parameter(description = "Filter: PAID or UNPAID")
            @RequestParam(required = false) PaidStatusFilter status,
            @RequestParam(defaultValue = "50") int limit) {
        List<StaffPaymentListItemResponse> payments = paymentService.getAllPaymentsForStaff(status, limit);
        return ResponseEntity.ok(ApiResponse.ok("Payments retrieved successfully", payments));
    }

    @Operation(summary = "Get payment history by driver ID")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    @GetMapping("/driver/{driverId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentHistoryByDriverId(
            Authentication auth,
            @PathVariable String driverId,
            @RequestParam(defaultValue = "20") int limit) {
        List<PaymentResponse> payments = paymentService.getPaymentHistoryByDriverId(
                driverId, auth.getName(), limit);
        return ResponseEntity.ok(ApiResponse.ok("Payment history retrieved successfully", payments));
    }

    @Operation(summary = "Get payment details by payment ID")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> getPaymentDetails(
            @PathVariable String paymentId) {
        PaymentResponseDTO response = paymentService.getPaymentDetails(paymentId);
        return ResponseEntity.ok(ApiResponse.ok("Payment details retrieved successfully", response));
    }
}
