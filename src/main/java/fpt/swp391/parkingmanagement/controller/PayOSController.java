package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.PaymentResponseDTO;
import fpt.swp391.parkingmanagement.enums.PaymentStatus;
import fpt.swp391.parkingmanagement.service.PayOSService;
import fpt.swp391.parkingmanagement.service.PaymentService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.payos.model.webhooks.ConfirmWebhookResponse;
import vn.payos.model.webhooks.WebhookData;

import java.io.IOException;
import java.util.Map;

/**
 * Xử lý callback từ PayOS:
 *  - Webhook (/payos/webhook) : PayOS gọi server-to-server → cập nhật DB
 *  - Return  (/payos/return)  : Trình duyệt user quay lại sau thanh toán thành công
 *  - Cancel  (/payos/cancel)  : User hủy thanh toán
 */
@RestController
@RequestMapping("/api/payments/payos")
public class PayOSController {

    private final PayOSService payOSService;
    private final PaymentService paymentService;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public PayOSController(PayOSService payOSService, PaymentService paymentService) {
        this.payOSService = payOSService;
        this.paymentService = paymentService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody Object body) {
        try {
            WebhookData data = payOSService.verifyWebhook(body);
            long orderCode = data.getOrderCode();
            String payosCode = data.getCode();

            PaymentResponseDTO existing = paymentService.getPaymentByOrderCode(orderCode);

            if (existing.getPaymentStatus() != PaymentStatus.PENDING) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Order already processed"
                ));
            }

            if ("00".equals(payosCode)) {
                String transactionId = data.getReference() != null
                        ? data.getReference()
                        : String.valueOf(orderCode);
                paymentService.confirmPaymentSuccess(existing.getPaymentId(), transactionId);
            } else {
                paymentService.handlePaymentFailure(existing.getPaymentId(),
                        "PayOS code: " + payosCode);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Webhook processed"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/return")
    public void handleReturn(
            @RequestParam("orderCode") long orderCode,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "status", required = false) String status,
            HttpServletResponse response) throws IOException {
        try {
            PaymentResponseDTO payment = paymentService.getPaymentByOrderCode(orderCode);

            if ("00".equals(code) || "PAID".equalsIgnoreCase(status)) {
                // Fallback: webhook chưa kịp chạy → tự cập nhật PAID tại đây
                if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
                    paymentService.confirmPaymentSuccess(
                            payment.getPaymentId(), String.valueOf(orderCode));
                }
                response.sendRedirect(frontendUrl + "/payment/success");
            } else {
                response.sendRedirect(frontendUrl + "/payment/failed");
            }
        } catch (Exception e) {
            response.sendRedirect(frontendUrl + "/payment/failed");
        }
    }

    @GetMapping("/cancel")
    public void handleCancel(
            @RequestParam("orderCode") long orderCode,
            HttpServletResponse response) throws IOException {
        try {
            PaymentResponseDTO payment = paymentService.getPaymentByOrderCode(orderCode);
            if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
                paymentService.handlePaymentFailure(payment.getPaymentId(), "User cancelled payment");
            }
        } catch (Exception ignored) {}
        response.sendRedirect(frontendUrl + "/payment/failed");
    }

    @PostMapping("/confirm-webhook")
    public ResponseEntity<?> confirmWebhook(@RequestBody Map<String, String> requestBody) {
        try {
            String webhookUrl = requestBody.get("webhookUrl");
            if (webhookUrl == null || webhookUrl.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "webhookUrl is required"
                ));
            }
            ConfirmWebhookResponse result = payOSService.confirmWebhook(webhookUrl);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
