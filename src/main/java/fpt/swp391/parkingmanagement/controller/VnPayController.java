package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.PaymentResponseDTO;
import fpt.swp391.parkingmanagement.service.PaymentService;
import fpt.swp391.parkingmanagement.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý 2 callback từ VNPay:
 *  - IPN  (/vnpay/ipn)    : VNPay gọi server-to-server → cập nhật DB
 *  - Return (/vnpay/return): Trình duyệt user quay lại → hiển thị kết quả
 */
@RestController
@RequestMapping("/api/payments/vnpay")
public class VnPayController {

    private final VnPayService vnPayService;
    private final PaymentService paymentService;

    public VnPayController(VnPayService vnPayService, PaymentService paymentService) {
        this.vnPayService = vnPayService;
        this.paymentService = paymentService;
    }

    /**
     * IPN – VNPay gọi server-to-server sau khi thanh toán.
     * Response phải là JSON {"RspCode":"00","Message":"Confirm Success"}.
     * Luôn trả 200 OK dù thành công hay thất bại (chỉ thay RspCode).
     */
    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> handleIpn(HttpServletRequest request) {
        Map<String, String> response = new HashMap<>();

        // Bước 1: Verify chữ ký
        if (!vnPayService.verifySignature(request)) {
            response.put("RspCode", "97");
            response.put("Message", "Invalid Checksum");
            return ResponseEntity.ok(response);
        }

        String paymentId = request.getParameter("vnp_TxnRef");
        String vnpResponseCode = request.getParameter("vnp_ResponseCode");
        String transactionNo = request.getParameter("vnp_TransactionNo");
        String vnpAmountStr = request.getParameter("vnp_Amount"); // đã × 100

        // Bước 2: Kiểm tra order tồn tại
        PaymentResponseDTO existing;
        try {
            existing = paymentService.getPaymentDetails(paymentId);
        } catch (Exception e) {
            response.put("RspCode", "01");
            response.put("Message", "Order not Found");
            return ResponseEntity.ok(response);
        }

        // Bước 3: Kiểm tra số tiền (VNPay trả × 100)
        if (existing.getAmount() != null && vnpAmountStr != null) {
            BigDecimal expectedAmount = existing.getAmount().multiply(BigDecimal.valueOf(100));
            BigDecimal receivedAmount = new BigDecimal(vnpAmountStr);
            if (expectedAmount.compareTo(receivedAmount) != 0) {
                response.put("RspCode", "04");
                response.put("Message", "Invalid Amount");
                return ResponseEntity.ok(response);
            }
        }

        // Bước 4: Kiểm tra trạng thái - chỉ xử lý khi còn PENDING (idempotent)
        if (!"PENDING".equals(existing.getPaymentStatus())) {
            response.put("RspCode", "02");
            response.put("Message", "Order already confirmed");
            return ResponseEntity.ok(response);
        }

        // Bước 5: Cập nhật DB
        if ("00".equals(vnpResponseCode)) {
            paymentService.confirmPaymentSuccess(paymentId, transactionNo);
        } else {
            paymentService.handlePaymentFailure(paymentId, "VNPay code: " + vnpResponseCode);
        }

        response.put("RspCode", "00");
        response.put("Message", "Confirm Success");
        return ResponseEntity.ok(response);
    }

    /**
     * Return URL – trình duyệt driver quay về sau khi thanh toán.
     * Chỉ hiển thị kết quả, KHÔNG cập nhật DB (đã được IPN xử lý).
     */
    @GetMapping("/return")
    public ResponseEntity<?> handleReturn(HttpServletRequest request) {
        if (!vnPayService.verifySignature(request)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", "Invalid signature"
            ));
        }

        String paymentId = request.getParameter("vnp_TxnRef");
        String responseCode = request.getParameter("vnp_ResponseCode");
        String transactionStatus = request.getParameter("vnp_TransactionStatus");

        try {
            PaymentResponseDTO payment = paymentService.getPaymentDetails(paymentId);
            if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "message", "Thanh toán thành công",
                        "payment", payment
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "status", "FAILED",
                        "message", "Thanh toán không thành công. Mã lỗi: " + responseCode,
                        "payment", payment
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }
}
