package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.QuickCheckinRequest;
import fpt.swp391.parkingmanagement.dto.QuickCheckinResponse;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
@Tag(name = "Quick Checkin", description = "Staff quét ảnh biển số — hệ thống tự OCR, tìm reservation / assign slot, tạo session. Không cần nhập tay.")
public class QuickSessionController {

    private final ParkingSessionService parkingSessionService;

    @Operation(summary = "Quick Check-in (Driver + Guest)",
            description = "Staff quét ảnh biển số → hệ thống tự nhận diện biển số bằng OCR. "
                    + "Nếu mode=DRIVER: tự tìm reservation PENDING/APPROVED theo biển số và tạo session. "
                    + "Nếu mode=GUEST: tự assign slot trống và tạo session vãng lai.")
    @PostMapping(value = "/quick-checkin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QuickCheckinResponse>> quickCheckin(
            @ModelAttribute QuickCheckinRequest req,
            Authentication auth) {

        // #region agent log
        try {
            String cwd = System.getProperty("user.dir");
            String tmp = System.getProperty("java.io.tmpdir");
            boolean plateEmpty = req.getPlateImage() == null || req.getPlateImage().isEmpty();
            String logPath = tmp + "debug-d41f02.log";
            String payload = String.format(
                "{\"sessionId\":\"d41f02\",\"id\":\"log_ctrl_entry_%d\",\"timestamp\":%d,\"location\":\"QuickSessionController.java:38\",\"message\":\"controller entered\",\"data\":{\"cwd\":\"%s\",\"tmp\":\"%s\",\"logPath\":\"%s\",\"plateImageEmpty\":%s,\"buildingId\":\"%s\",\"mode\":\"%s\",\"email\":\"%s\"},\"runId\":\"debug-2\",\"hypothesisId\":\"F,G,H\"}\n",
                System.nanoTime(), System.currentTimeMillis(),
                cwd == null ? "" : cwd.replace("\"","\\\""),
                tmp == null ? "" : tmp.replace("\"","\\\""),
                logPath.replace("\"","\\\""),
                plateEmpty,
                req.getBuildingId() == null ? "" : req.getBuildingId(),
                req.getMode() == null ? "" : req.getMode().name(),
                auth.getName() == null ? "" : auth.getName());
            Files.writeString(Path.of(logPath), payload,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
        // #endregion

        String email = auth.getName();
        QuickCheckinRequest.QuickMode mode = req.getMode() != null
                ? req.getMode()
                : QuickCheckinRequest.QuickMode.DRIVER;

        QuickCheckinResponse result;
        if (mode == QuickCheckinRequest.QuickMode.GUEST) {
            result = parkingSessionService.quickGuestCheckin(email, req);
        } else {
            result = parkingSessionService.quickDriverCheckin(email, req);
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
