package fpt.swp391.parkingmanagement.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CheckinRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.EstimateResponse;
import fpt.swp391.parkingmanagement.dto.ParkingSessionResponse;
import fpt.swp391.parkingmanagement.service.CloudinaryService;
import fpt.swp391.parkingmanagement.service.ParkingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
public class ParkingSessionController {

    private final ParkingSessionService parkingSessionService;
    private final CloudinaryService cloudinaryService;

    @Operation(summary = "Staff Check-in", description = "Staff quét vé để cho xe vào bãi. Tạo ParkingSession, đánh dấu slot OCCUPIED. Gửi multipart/form-data.")
    @PostMapping(value = "/sessions/checkin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ParkingSessionResponse>> checkin(
            @RequestParam String ticketCode,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String vehicleColor,
            @RequestParam(required = false) String vehicleTypeId,
            @RequestParam(required = false) MultipartFile checkinImage,
            Authentication auth) {
        CheckinRequest req = new CheckinRequest();
        req.setTicketCode(ticketCode);
        req.setPlateNumber(plateNumber);
        req.setVehicleColor(vehicleColor);
        req.setVehicleTypeId(vehicleTypeId);
        if (checkinImage != null && !checkinImage.isEmpty()) {
            req.setCheckinImageUrl(cloudinaryService.uploadParkingImage(checkinImage));
        }
        ParkingSessionResponse resp = parkingSessionService.checkin(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Check-in successful", resp));
    }

    @Operation(summary = "Staff Check-out", description = "Staff quét vé khi xe ra. Tính phí, giải phóng slot, đánh dấu reservation COMPLETED. Gửi multipart/form-data.")
    @PostMapping(value = "/sessions/checkout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @RequestParam String ticketCode,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) MultipartFile checkoutImage,
            Authentication auth) {
        CheckoutRequest req = new CheckoutRequest();
        req.setTicketCode(ticketCode);
        req.setPaymentMethod(paymentMethod);
        if (checkoutImage != null && !checkoutImage.isEmpty()) {
            req.setCheckoutImageUrl(cloudinaryService.uploadParkingImage(checkoutImage));
        }
        CheckoutResponse resp = parkingSessionService.checkout(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Checkout successful", resp));
    }

    @Operation(summary = "Estimate fee for a session",
               description = "Driver/Staff xem chi tiết phí trước khi tạo payment. Không tạo payment record, chỉ tính và trả breakdown.")
    @GetMapping("/sessions/estimate")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN','DRIVER')")
    public ResponseEntity<ApiResponse<EstimateResponse>> estimateFee(
            @RequestParam String ticketCode,
            Authentication auth) {
        EstimateResponse resp = parkingSessionService.estimateFee(ticketCode);
        return ResponseEntity.ok(ApiResponse.ok("Fee estimated successfully", resp));
    }

    @Operation(summary = "Staff checkout after electronic payment",
               description = "After driver completes VNPay/PayOS/MOMO payment (session paymentStatus = PAID), staff calls this to release the slot.")
    @PatchMapping("/sessions/{sessionId}/confirm-exit")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutResponse>> confirmExit(
            @PathVariable String sessionId,
            @RequestParam(required = false) String paymentMethod,
            Authentication auth) {
        CheckoutResponse resp = parkingSessionService.confirmExitAndCheckout(
                auth.getName(), sessionId, paymentMethod);
        return ResponseEntity.ok(ApiResponse.ok("Exit confirmed, driver may proceed", resp));
    }
}
