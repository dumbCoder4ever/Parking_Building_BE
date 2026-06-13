package fpt.swp391.parkingmanagement.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CheckinRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.EstimateResponse;
import fpt.swp391.parkingmanagement.dto.ParkingSessionResponse;
import fpt.swp391.parkingmanagement.service.ParkingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
public class ParkingSessionController {

    private final ParkingSessionService parkingSessionService;

    @Operation(summary = "Staff Check-in", description = "Staff quét vé để cho xe vào bãi. Tạo ParkingSession, đánh dấu slot OCCUPIED.")
    @PostMapping("/sessions/checkin")
    public ResponseEntity<ApiResponse<ParkingSessionResponse>> checkin(
            @Valid @RequestBody CheckinRequest req,
            Authentication auth) {
        ParkingSessionResponse resp = parkingSessionService.checkin(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Check-in successful", resp));
    }

    @Operation(summary = "Staff Check-out", description = "Staff quét vé khi xe ra. Tính phí, giải phóng slot, đánh dấu reservation COMPLETED.")
    @PostMapping("/sessions/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @Valid @RequestBody CheckoutRequest req,
            Authentication auth) {
        CheckoutResponse resp = parkingSessionService.checkout(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Checkout successful", resp));
    }

    @Operation(summary = "Estimate fee for a session",
               description = "Driver/Staff xem chi tiết phí trước khi tạo payment. Không tạo payment record, chỉ tính và trả breakdown.")
    @GetMapping("/sessions/estimate")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN','DRIVER')")
    public ResponseEntity<ApiResponse<EstimateResponse>> estimateFee(
            @RequestParam String ticketCode,
            @RequestParam(defaultValue = "false") boolean lostTicket,
            Authentication auth) {
        EstimateResponse resp = parkingSessionService.estimateFee(ticketCode);
        return ResponseEntity.ok(ApiResponse.ok("Fee estimated successfully", resp));
    }

    @Operation(summary = "Staff xác nhận driver đã thanh toán → cho xe ra",
               description = "Sau khi driver thanh toán xong (VNPay/PayOS/MOMO webhook confirmed), staff gọi API này để confirm exit. Slot được giải phóng, reservation = COMPLETED.")
    @PatchMapping("/sessions/{sessionId}/confirm-exit")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutResponse>> confirmExit(
            @PathVariable String sessionId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(defaultValue = "false") boolean lostTicket,
            Authentication auth) {
        CheckoutResponse resp = parkingSessionService.confirmExitAndCheckout(
                auth.getName(), sessionId, paymentMethod);
        return ResponseEntity.ok(ApiResponse.ok("Exit confirmed, driver may proceed", resp));
    }
}
