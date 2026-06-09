package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.PaymentRequestDTO;
import fpt.swp391.parkingmanagement.dto.PaymentResponseDTO;
import fpt.swp391.parkingmanagement.dto.PaymentConfirmationDTO;
import fpt.swp391.parkingmanagement.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    @Autowired
    private PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponseDTO> initiatePayment(
            @RequestBody PaymentRequestDTO paymentRequest) {
        try {
            PaymentResponseDTO response = paymentService.initiatePayment(paymentRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PaymentResponseDTO.builder()
                            .message("Error initiating payment: " + e.getMessage())
                            .build());
        }
    }

    @PostMapping("/confirm-success")
    public ResponseEntity<PaymentResponseDTO> confirmPaymentSuccess(
            @RequestParam String paymentId,
            @RequestParam String transactionCode) {
        try {
            PaymentResponseDTO response = paymentService.confirmPaymentSuccess(paymentId, transactionCode);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PaymentResponseDTO.builder()
                            .message("Error confirming payment: " + e.getMessage())
                            .build());
        }
    }

    @PostMapping("/confirm-by-staff")
    public ResponseEntity<PaymentConfirmationDTO> confirmPaymentByStaff(
            @RequestBody PaymentConfirmationDTO confirmationRequest) {
        try {
            PaymentConfirmationDTO response = paymentService.confirmPaymentByStaff(confirmationRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PaymentConfirmationDTO.builder()
                            .confirmationStatus("ERROR")
                            .reason(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/handle-failure")
    public ResponseEntity<PaymentResponseDTO> handlePaymentFailure(
            @RequestParam String paymentId,
            @RequestParam String reason) {
        try {
            PaymentResponseDTO response = paymentService.handlePaymentFailure(paymentId, reason);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PaymentResponseDTO.builder()
                            .message("Error handling payment failure: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponseDTO> getPaymentDetails(
            @PathVariable String paymentId) {
        try {
            PaymentResponseDTO response = paymentService.getPaymentDetails(paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(PaymentResponseDTO.builder()
                            .message("Payment not found: " + e.getMessage())
                            .build());
        }
    }
}
