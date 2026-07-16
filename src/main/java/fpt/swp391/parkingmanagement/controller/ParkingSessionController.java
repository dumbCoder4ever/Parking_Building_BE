package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CheckinRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.EstimateResponse;
import fpt.swp391.parkingmanagement.dto.ParkingSessionResponse;
import fpt.swp391.parkingmanagement.dto.PlateLookupResponse;
import fpt.swp391.parkingmanagement.dto.QuickCheckinRequest;
import fpt.swp391.parkingmanagement.dto.QuickCheckinResponse;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.service.CloudinaryService;
import fpt.swp391.parkingmanagement.service.ParkingSessionService;
import fpt.swp391.parkingmanagement.service.PlateRecognizerService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Operation(summary = "Lookup plate for staff check-in",
            description = """
                    Tra cứu nhanh biển số trước khi check-in.
                    - Ưu tiên reservation PENDING/APPROVED (driver).
                    - Nếu không có reservation thì trả guest session ACTIVE (nếu có).
                    - `buildingId` tùy chọn để lọc reservation theo bãi.
                    """)
    @GetMapping("/sessions/plate/{plateNumber}/lookup")
    public ResponseEntity<ApiResponse<PlateLookupResponse>> lookupByPlate(
            @PathVariable String plateNumber,
            @RequestParam(required = false) String buildingId,
            Authentication auth) {
        PlateLookupResponse resp = parkingSessionService.lookupByPlate(plateNumber, buildingId);
        return ResponseEntity.ok(ApiResponse.ok("Plate lookup completed", resp));
    }

    @Operation(summary = "Unified Staff Check-in",
            description = """
                    Staff check-in hợp nhất. Gửi multipart/form-data.
                    - Test nhanh: gửi `ticketCode` + `plateNumber` hoặc `checkinImage`.
                    - Nếu gửi `plateImage` + `buildingId` thì dùng OCR quick auto check-in: DRIVER nếu đã có reservation, GUEST nếu vãng lai.
                    - GUEST có thể bổ sung `vehicleTypeId`, `guestName`, `guestPhone`, `note`.
                    Lưu ý: authentication header bắt buộc.
                    """)
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

        if (plateImage != null && !plateImage.isEmpty() && buildingId != null && !buildingId.isBlank()) {
            QuickCheckinRequest req = new QuickCheckinRequest();
            req.setPlateImage(plateImage);
            req.setBuildingId(buildingId);
            req.setVehicleTypeId(vehicleTypeId);
            req.setVehicleColor(vehicleColor);
            req.setGuestName(guestName);
            req.setGuestPhone(guestPhone);
            req.setNote(note);
            MultipartFile imageToUpload = checkinImage != null && !checkinImage.isEmpty()
                    ? checkinImage : plateImage;
            req.setCheckinImageUrl(cloudinaryService.uploadParkingImage(imageToUpload));

            QuickCheckinResponse result = parkingSessionService.quickAutoCheckin(auth.getName(), req);
            return ResponseEntity.ok(ApiResponse.ok("Check-in successful", result));
        }

        CheckinRequest req = new CheckinRequest();
        req.setTicketCode(ticketCode);
        req.setPlateNumber(plateNumber);
        req.setVehicleColor(vehicleColor);
        req.setVehicleTypeId(vehicleTypeId);
        req.setGuestName(guestName);
        req.setGuestPhone(guestPhone);
        req.setNote(note);
        MultipartFile imageToUpload = checkinImage != null ? checkinImage : plateImage;
        if (imageToUpload != null && !imageToUpload.isEmpty()) {
            req.setCheckinImageUrl(cloudinaryService.uploadParkingImage(imageToUpload));
        }
        ParkingSessionResponse resp = parkingSessionService.checkin(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Check-in successful", resp));
    }

    @Operation(summary = "Unified Staff Check-out",
            description = """
                    Staff checkout thống nhất, gửi multipart/form-data.
                    - Bắt buộc có `ticketCode`.
                    - `paymentMethod`: CASH, VNPAY, PAYOS, MOMO.
                    - Có thể bổ sung `checkoutImage`.
                    - Nếu gửi kèm `plateImage` thì hệ thống OCR biển số và so khớp với session trước khi checkout.
                    Lưu ý: authentication header bắt buộc.
                    """)
    @PostMapping(value = "/sessions/checkout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @RequestParam(required = false) String ticketCode,
            @RequestParam(required = false) MultipartFile plateImage,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) MultipartFile checkoutImage,
            Authentication auth) {

        CheckoutRequest checkoutRequest = buildCheckoutRequest(ticketCode, paymentMethod, checkoutImage);

        if (ticketCode != null && !ticketCode.isBlank() && plateImage != null && !plateImage.isEmpty()) {
            String plateNumber = recognizePlateOrThrow(plateImage);
            CheckoutResponse resp = parkingSessionService.guestCheckoutOcr(auth.getName(), checkoutRequest, plateNumber);
            return ResponseEntity.ok(ApiResponse.ok("Checkout successful", resp));
        }

        if (ticketCode != null && !ticketCode.isBlank()) {
            CheckoutResponse resp = parkingSessionService.checkout(auth.getName(), checkoutRequest);
            return ResponseEntity.ok(ApiResponse.ok("Checkout successful", resp));
        }

        throw new BaseAPIException(ErrorCode.BAD_REQUEST, "ticketCode or ticketCode + plateImage is required");
    }

    private String recognizePlateOrThrow(MultipartFile plateImage) {
        if (plateImage == null || plateImage.isEmpty()) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST, "Plate image is required");
        }
        try {
            PlateRecognizerService.OcrResult result = parkingSessionService.recognizePlate(plateImage);
            if (result.plateNumber() == null || result.plateNumber().isBlank()) {
                throw new BaseAPIException(ErrorCode.OCR_PLATE_NOT_DETECTED);
            }
            return result.plateNumber().toUpperCase();
        } catch (BaseAPIException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED, ex.getMessage());
        }
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

    @Operation(summary = "Driver Check-out (sau khi thanh toán)",
            description = """
                    Staff xác nhận xe ra cho driver đã thanh toán VNPay/PayOS/MOMO.
                    - Bắt buộc: `ticketCode`.
                    - Yêu cầu session.paymentStatus=PAID. Không tạo Payment.
                    - Có thể gửi `checkoutImage` ghi nhận ảnh xe ra.
                    Lưu ý: authentication header bắt buộc.
                    """)
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

    @Operation(summary = "Estimate fee for a session",
            description = """
                    Xem chi tiết phí trước khi checkout.
                    - Driver/Staff/Manager/Admin đều gọi được.
                    - Bắt buộc có `ticketCode`.
                    """)
    @GetMapping("/sessions/estimate")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN','DRIVER')")
    public ResponseEntity<ApiResponse<EstimateResponse>> estimateFee(
            @RequestParam String ticketCode,
            Authentication auth) {
        EstimateResponse resp = parkingSessionService.estimateFee(ticketCode);
        return ResponseEntity.ok(ApiResponse.ok("Fee estimated successfully", resp));
    }

    @Operation(summary = "Staff checkout after electronic payment",
            description = """
                    Dùng khi driver đã thanh toán VNPay/PayOS/MOMO và session.paymentStatus = PAID.
                    - Staff gọi để release slot sau khi kiểm tra payment thành công.
                    - Có thể gửi `paymentMethod` và `checkoutImage`.
                    Lưu ý: authentication header bắt buộc.
                    """)
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
