package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CheckinRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.EstimateResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckoutRequest;
import fpt.swp391.parkingmanagement.dto.ParkingSessionResponse;
import fpt.swp391.parkingmanagement.dto.QuickCheckinRequest;
import fpt.swp391.parkingmanagement.dto.QuickCheckinResponse;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.service.CloudinaryService;
import fpt.swp391.parkingmanagement.service.ParkingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
public class ParkingSessionController {

    private final ParkingSessionService parkingSessionService;
    private final CloudinaryService cloudinaryService;
    private final TicketRepository ticketRepository;

    /**
     * Unified check-in endpoint.
     *
     * <p>Legacy path: client gửi {@code ticketCode} + {@code plateNumber} → checkin() manual driver.</p>
     * <p>OCR path: client gửi {@code plateImage} + {@code buildingId} → auto-detect DRIVER/GUEST.</p>
     */
    @Operation(summary = "Unified Staff Check-in",
            description = "Staff check-in hợp nhất: ticket thủ công (ticketCode + plateNumber) HOẶC OCR (plateImage + buildingId, auto-detect DRIVER/GUEST).")
    @PostMapping(value = "/sessions/checkin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> checkin(
            @RequestParam(required = false) String ticketCode,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String vehicleColor,
            @RequestParam(required = false) String vehicleTypeId,
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String guestName,
            @RequestParam(required = false) String guestPhone,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) MultipartFile checkinImage,
            @RequestParam(required = false) MultipartFile plateImage,
            Authentication auth) {

        // [DEBUG] Log entry point
        System.out.println("[DEBUG-6b654b] CONTROLLER checkin - auth: " + (auth != null ? auth.getName() : "NULL") 
                + ", ticketCode: " + ticketCode + ", buildingId: " + buildingId
                + ", hasPlateImage: " + (plateImage != null && !plateImage.isEmpty()));
        
        // ========== OCR MODE: Staff upload ảnh plate → AUTO DETECT DRIVER/GUEST ==========
        if (plateImage != null && !plateImage.isEmpty() && buildingId != null && !buildingId.isBlank()) {
            QuickCheckinRequest req = new QuickCheckinRequest();
            req.setPlateImage(plateImage);
            req.setBuildingId(buildingId);
            req.setVehicleTypeId(vehicleTypeId);

            // AUTO DETECT: Tự động detect DRIVER (plate có reservation) hoặc GUEST (plate không có reservation)
            QuickCheckinResponse result = parkingSessionService.quickAutoCheckin(auth.getName(), req);
            return ResponseEntity.ok(ApiResponse.ok("Check-in successful", result));
        }

        // ========== LEGACY MODE: Manual checkin với ticketCode + plateNumber ==========
        CheckinRequest req = new CheckinRequest();
        req.setTicketCode(ticketCode);
        req.setPlateNumber(plateNumber);
        req.setVehicleColor(vehicleColor);
        req.setVehicleTypeId(vehicleTypeId);
        MultipartFile imageToUpload = checkinImage != null ? checkinImage : plateImage;
        if (imageToUpload != null && !imageToUpload.isEmpty()) {
            req.setCheckinImageUrl(cloudinaryService.uploadParkingImage(imageToUpload));
        }
        ParkingSessionResponse resp = parkingSessionService.checkin(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Check-in successful", resp));
    }

    /**
     * Driver checkout: chỉ xác nhận xe ra sau khi đã thanh toán (PAID).
     * Không tạo Payment record — payment xử lý qua /payments trước.
     *
     * @deprecated dùng {@link #driverCheckout} thay thế.
     */
    @Deprecated
    @Operation(summary = "Staff Check-out (legacy, dùng cho cả driver & guest)",
            description = "DEPRECATED: dùng /sessions/driver/checkout hoặc /sessions/guest/checkout. Sẽ bị xóa khi FE migrate xong.")
    @PostMapping(value = "/sessions/checkout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @RequestParam String ticketCode,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) MultipartFile checkoutImage,
            Authentication auth) {
        CheckoutResponse resp = parkingSessionService.checkout(auth.getName(),
                buildCheckoutRequest(ticketCode, paymentMethod, checkoutImage));
        return ResponseEntity.ok(ApiResponse.ok("Checkout successful", resp));
    }

    private CheckoutRequest buildCheckoutRequest(
            String ticketCode, String paymentMethod, MultipartFile checkoutImage) {
        CheckoutRequest req = new CheckoutRequest();
        req.setTicketCode(ticketCode);
        req.setPaymentMethod(paymentMethod);
        if (checkoutImage != null && !checkoutImage.isEmpty()) {
            req.setCheckoutImageUrl(cloudinaryService.uploadParkingImage(checkoutImage));
        }
        return req;
    }

    /**
     * Driver checkout: chỉ xác nhận xe ra sau khi đã thanh toán (PAID).
     * Không tạo Payment record — payment xử lý qua /payments trước.
     */
    @Operation(summary = "Driver Check-out (sau khi thanh toán)",
            description = "Staff xác nhận xe ra cho driver đã thanh toán (VNPay/PayOS/MOMO). Yêu cầu session.paymentStatus=PAID. Không tạo Payment.")
    @PostMapping(value = "/sessions/driver/checkout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CheckoutResponse>> driverCheckout(
            @RequestParam String ticketCode,
            @RequestParam(required = false) MultipartFile checkoutImage,
            Authentication auth) {
        String sessionId = parkingSessionService.findSessionIdByTicketCode(ticketCode)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND,
                        "No active session for ticket: " + ticketCode));
        CheckoutResponse resp = parkingSessionService.confirmExitAndCheckout(
                auth.getName(), sessionId, null, checkoutImage != null ? cloudinaryService.uploadParkingImage(checkoutImage) : null);
        return ResponseEntity.ok(ApiResponse.ok("Driver checkout successful", resp));
    }

    /**
     * Guest checkout: tính phí + xử lý payment (CASH tạo Payment PAID, electronic yêu cầu PAID trước).
     *
     * <p>Path mới: {@code /api/sessions/guest/checkout/v2} — đặt {@code /v2} để khỏi trùng với
     * {@code GuestSessionController#guestCheckout} đang giữ ở path cũ (backward-compat cho FE).</p>
     */
    @Operation(summary = "Guest Check-out (tính phí + xử lý payment)",
            description = "Staff checkout khách vãng lai. Với CASH: tạo Payment(PAID) inline. Với VNPay/PayOS/MOMO: yêu cầu đã có paymentStatus=PAID.")
    @PostMapping(value = "/sessions/guest/checkout/v2", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CheckoutResponse>> guestCheckoutV2(
            @RequestParam String plateNumber,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) MultipartFile checkoutImage,
            Authentication auth) {
        GuestCheckoutRequest req = new GuestCheckoutRequest();
        req.setPlateNumber(plateNumber != null ? plateNumber.toUpperCase() : null);
        req.setPaymentMethod(paymentMethod);
        if (checkoutImage != null && !checkoutImage.isEmpty()) {
            req.setCheckoutImageUrl(cloudinaryService.uploadParkingImage(checkoutImage));
        }
        CheckoutResponse resp = parkingSessionService.guestCheckout(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Guest checkout successful", resp));
    }

    @Operation(summary = "Estimate fee for a session",
            description = "Driver/Staff xem chi tiết phí trước khi tạo payment.")
    @GetMapping("/sessions/estimate")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN','DRIVER')")
    public ResponseEntity<ApiResponse<EstimateResponse>> estimateFee(
            @RequestParam String ticketCode,
            Authentication auth) {
        EstimateResponse resp = parkingSessionService.estimateFee(ticketCode);
        return ResponseEntity.ok(ApiResponse.ok("Fee estimated successfully", resp));
    }

    @Operation(summary = "Staff checkout after electronic payment",
            description = "Sau khi driver hoàn tất VNPay/PayOS/MOMO (session.paymentStatus = PAID), staff gọi để release slot.")
    @PatchMapping(value = "/sessions/{sessionId}/confirm-exit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutResponse>> confirmExit(
            @PathVariable String sessionId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) MultipartFile checkoutImage,
            Authentication auth) {
        CheckoutResponse resp = parkingSessionService.confirmExitAndCheckout(
                auth.getName(), sessionId, paymentMethod, checkoutImage != null ? cloudinaryService.uploadParkingImage(checkoutImage) : null);
        return ResponseEntity.ok(ApiResponse.ok("Exit confirmed, driver may proceed", resp));
    }
}