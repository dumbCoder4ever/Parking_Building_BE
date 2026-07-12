package fpt.swp391.parkingmanagement.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckinOcrRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckinRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckinResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckoutOcrRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckoutRequest;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.service.CloudinaryService;
import fpt.swp391.parkingmanagement.service.PlateRecognizerService;
import fpt.swp391.parkingmanagement.service.PlateRecognizerService.OcrResult;
import fpt.swp391.parkingmanagement.service.ParkingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/guest")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
@Tag(name = "guest-parking-controller")
public class GuestSessionController {

    private final ParkingSessionService parkingSessionService;
    private final CloudinaryService cloudinaryService;
    private final PlateRecognizerService ocrService;

    @Operation(
        summary = "Guest Check-in",
        description = "Staff chụp/upload ảnh biển số xe khách vãng lai. Hệ thống dùng Plate Recognizer OCR đọc biển số, "
                    + "tạo ParkingSession và gán slot. Chỉ cần gửi multipart/form-data với plateImage và slotId."
    )
    @PostMapping(value = "/checkin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<GuestCheckinResponse>> guestCheckin(
            @RequestParam String slotId,
            @RequestParam MultipartFile plateImage,
            @RequestParam(required = false) String note,
            Authentication auth) {

        String plateNumber = detectPlateOrThrow(plateImage);
        String imageUrl = cloudinaryService.uploadParkingImage(plateImage);

        GuestCheckinRequest req = new GuestCheckinRequest();
        req.setPlateNumber(plateNumber);
        req.setSlotId(slotId);
        req.setNote(note);
        req.setCheckinImageUrl(imageUrl);

        GuestCheckinResponse resp = parkingSessionService.guestCheckin(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Guest check-in successful", resp));
    }

    @Operation(
        summary = "Guest Check-out",
        description = "Staff chụp/upload ảnh biển số để checkout khách vãng lai. OCR đọc biển số, "
                    + "tra cứu phiên ACTIVE và tính phí. Gửi multipart/form-data với plateImage."
    )
    @PostMapping(value = "/checkout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CheckoutResponse>> guestCheckout(
            @RequestParam MultipartFile plateImage,
            @RequestParam(required = false) String paymentMethod,
            Authentication auth) {

        String plateNumber = detectPlateOrThrow(plateImage);
        String imageUrl = cloudinaryService.uploadParkingImage(plateImage);

        GuestCheckoutRequest req = new GuestCheckoutRequest();
        req.setPlateNumber(plateNumber);
        req.setPaymentMethod(paymentMethod);
        req.setCheckoutImageUrl(imageUrl);

        CheckoutResponse resp = parkingSessionService.guestCheckout(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Guest checkout successful", resp));
    }

    @Operation(
        summary = "Get guest session by ID",
        description = "Lấy thông tin phiên đỗ xe của khách vãng lai theo sessionId."
    )
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<GuestCheckinResponse>> getGuestSession(
            @PathVariable String sessionId,
            Authentication auth) {

        GuestCheckinResponse resp = parkingSessionService.getGuestSessionById(sessionId);
        return ResponseEntity.ok(ApiResponse.ok("Guest session found", resp));
    }

    @Operation(
        summary = "Find active guest session by plate number",
        description = "Tra cứu phiên đỗ xe đang hoạt động của khách vãng lai theo biển số xe."
    )
    @GetMapping("/plate/{plateNumber}")
    public ResponseEntity<ApiResponse<GuestCheckinResponse>> findByPlate(
            @PathVariable String plateNumber,
            Authentication auth) {

        GuestCheckinResponse resp = parkingSessionService.findActiveGuestByPlate(plateNumber);
        return ResponseEntity.ok(ApiResponse.ok("Active guest session found", resp));
    }

    @Operation(
        summary = "Look up guest session by ticket code",
        description = "Staff tra cứu thông tin session và phí theo ticket code (in khi check-in)."
    )
    @GetMapping("/ticket/{ticketCode}")
    public ResponseEntity<ApiResponse<GuestCheckinResponse>> findByTicketCode(
            @PathVariable String ticketCode,
            Authentication auth) {

        GuestCheckinResponse resp = parkingSessionService.getGuestSessionByTicketCode(ticketCode);
        return ResponseEntity.ok(ApiResponse.ok("Guest session found", resp));
    }

    // ======================== OCR-BASED ENDPOINTS ========================

    @Operation(
        summary = "Guest Check-in bằng OCR (quét biển số)",
        description = "Staff chỉ cần quét ảnh biển số xe. Hệ thống tự nhận diện biển số → auto-assign slot trống → tạo session. "
                    + "Nếu biển số đã đang đỗ trong bãi → báo lỗi PLATE_ALREADY_PARKED. "
                    + "Nếu không có slot trống → báo lỗi SLOT_NOT_AVAILABLE."
    )
    @PostMapping(value = "/checkin/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<GuestCheckinResponse>> guestCheckinOcr(
            @ModelAttribute GuestCheckinOcrRequest req,
            Authentication auth) {

        if (req.getCheckinImage() != null && !req.getCheckinImage().isEmpty()) {
            req.setCheckinImageUrl(cloudinaryService.uploadParkingImageSafe(req.getCheckinImage()));
        }

        GuestCheckinResponse resp = parkingSessionService.guestCheckinOcr(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Guest check-in via OCR successful", resp));
    }

    @Operation(
        summary = "Guest Check-out bằng OCR (quét biển số)",
        description = "Staff quét ảnh biển số xe lúc ra + nhập ticketCode. "
                    + "Hệ thống tự nhận diện biển số → validate khớp với session → checkout. "
                    + "Nếu biển số quét không khớp → báo lỗi PLATE_MISMATCH. "
                    + "Thanh toán CASH mặc định, hỗ trợ VNPAY/PAYOS/MOMO."
    )
    @PostMapping(value = "/checkout/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CheckoutResponse>> guestCheckoutOcr(
            @ModelAttribute GuestCheckoutOcrRequest req,
            Authentication auth) {

        if (req.getCheckoutImage() != null && !req.getCheckoutImage().isEmpty()) {
            req.setCheckoutImageUrl(cloudinaryService.uploadParkingImageSafe(req.getCheckoutImage()));
        }

        CheckoutResponse resp = parkingSessionService.guestCheckoutOcr(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Guest checkout via OCR successful", resp));
    }

    private String detectPlateOrThrow(MultipartFile plateImage) {
        if (plateImage == null || plateImage.isEmpty()) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST, "Plate image is required");
        }
        try {
            OcrResult result = ocrService.recognizeFromUpload(plateImage);
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
}
