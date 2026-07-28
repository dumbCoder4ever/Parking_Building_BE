package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.QuickCheckinRequest;
import fpt.swp391.parkingmanagement.dto.QuickCheckinResponse;
import fpt.swp391.parkingmanagement.service.CloudinaryService;
import fpt.swp391.parkingmanagement.service.ParkingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @deprecated dùng {@code POST /api/sessions/checkin} (ParkingSessionController) với {@code plateImage}.
 * Giữ tạm để FE chuyển đổi dần. Sẽ bị xóa khi FE đã migrate hết.
 */
@Deprecated
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
@Tag(name = "Quick Checkin (Deprecated)", description = "Will be removed. Use POST /api/sessions/checkin with plateImage.")
public class QuickSessionController {

    private final ParkingSessionService parkingSessionService;
    private final CloudinaryService cloudinaryService;

    @Operation(summary = "Quick Check-in (Driver + Guest) — DEPRECATED",
            description = "Deprecated endpoint. Use POST /api/sessions/checkin with plateImage instead.")
    @PostMapping(value = "/quick-checkin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QuickCheckinResponse>> quickCheckin(
            @ModelAttribute QuickCheckinRequest req,
            Authentication auth) {

        if (req.getPlateImage() != null && !req.getPlateImage().isEmpty()) {
            req.setCheckinVehicleImage(cloudinaryService.uploadParkingImage(req.getPlateImage()));
        }

        // AUTO DETECT: Tự động detect DRIVER/GUEST dựa trên biển số
        QuickCheckinResponse result = parkingSessionService.quickAutoCheckin(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}