package fpt.swp391.parkingmanagement.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckinResponse;
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

    @Operation(
            summary = "Get guest session by ID",
            description = """
                    Lấy thông tin phiên đỗ xe của khách vãng lai theo sessionId.
                    - Dùng khi staff cần xem chi tiết session sau khi tra cứu.
                    - Lưu ý: authentication header bắt buộc.
                    """
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
            description = """
                    Tra cứu phiên đỗ xe đang hoạt động của khách vãng lai theo biển số xe.
                    - Dùng khi checkout xe ra mà không có ticketCode trên tay.
                    - Nếu không có session ACTIVE thì trả về rỗng/lỗi theo rule hiện tại.
                    - Lưu ý: authentication header bắt buộc.
                    """
    )
    @GetMapping("/plate/{plateNumber}")
    public ResponseEntity<ApiResponse<GuestCheckinResponse>> findByPlate(
            @PathVariable String plateNumber,
            Authentication auth) {

        GuestCheckinResponse resp = parkingSessionService.findActiveGuestByPlate(plateNumber);
        return ResponseEntity.ok(ApiResponse.ok("Plate lookup successful", resp));
    }

    @Operation(
            summary = "Look up guest session by ticket code",
            description = """
                    Staff tra cứu thông tin session và phí theo ticket code (in khi check-in).
                    - Dùng trước khi checkout để kiểm tra session, slot, phí.
                    - Lưu ý: authentication header bắt buộc.
                    """
    )
    @GetMapping("/ticket/{ticketCode}")
    public ResponseEntity<ApiResponse<GuestCheckinResponse>> findByTicketCode(
            @PathVariable String ticketCode,
            Authentication auth) {

        GuestCheckinResponse resp = parkingSessionService.getGuestSessionByTicketCode(ticketCode);
        return ResponseEntity.ok(ApiResponse.ok("Guest session found", resp));
    }
}
