package fpt.swp391.parkingmanagement.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import fpt.swp391.parkingmanagement.dto.CreateReservationRequest;
import fpt.swp391.parkingmanagement.dto.ReservationResponse;
import fpt.swp391.parkingmanagement.dto.SlotAvailabilityDto;
import fpt.swp391.parkingmanagement.dto.UpdateReservationStatusRequest;
import fpt.swp391.parkingmanagement.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * =============================================================================
 * RESERVATION FLOW - API cho User đặt trước slot
 * =============================================================================
 * 
 * FLOW 1 — USER ĐẶT TRƯỚC SLOT:
 * Bước 1: User login
 * Bước 2: Xem slot còn trống (floor -> vehicle type -> zone -> slot)
 * Bước 3: Nhập thông tin xe (biển số, màu, hãng, model, loại xe)
 * Bước 4: Chọn thời gian gửi (start -> end)
 * Bước 5: Tạo Reservation -> Slot: AVAILABLE -> RESERVED
 * Bước 6: Sinh Ticket (ticket_code, qr_code)
 * 
 * FLOW 2 — STAFF XÁC NHẬN XE VÀO BÃI:
 * Bước 1: User đến bãi xe (đưa ticket, QR, biển số)
 * Bước 2: Staff kiểm tra (ticket, biển số, loại xe, màu xe)
 * Bước 3: Nếu hợp lệ -> tạo Parking Session, Slot: RESERVED -> OCCUPIED
 * Bước 4: Nếu không hợp lệ -> Staff từ chối check-in
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    // =========================================================================
    // FLOW 1: USER ĐẶT TRƯỚC SLOT
    // =========================================================================

    /**
     * Bước 2: Xem slot còn trống theo Building, Vehicle Type
     * 
     * GET /api/slots/availability
     * 
     * Trả về hierarchy: Building -> Floor -> Zone -> Slots
     * Mỗi zone hiển thị: totalSlots, availableSlots
     * 
     * Ví dụ response:
     * - Building A: Floor 1: Motorbike -> Zone A (20/20 available), Zone B (15/20 available)
     * - Building B: Floor 2: Car -> Zone C (5/10 available)
     */
    @GetMapping("/slots/availability")
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<SlotAvailabilityDto>>> getAvailableSlots(
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String vehicleTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Available slots retrieved successfully",
                reservationService.getAvailability(buildingId, vehicleTypeId)));
    }

    /**
     * Bước 5: Tạo Reservation
     * 
     * POST /api/reservations
     * 
     * User chọn slot + nhập thông tin xe + chọn thời gian
     * Hệ thống sẽ:
     * - Tạo reservation
     * - Đổi slot: AVAILABLE -> RESERVED
     * - Sinh ticket_code và qr_code
     */
    @PostMapping("/reservations")
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody CreateReservationRequest req, 
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation created successfully. Please show your ticket when checking in.",
                reservationService.createReservation(auth.getName(), req)));
    }

    /**
     * Xem reservation của tôi
     * 
     * GET /api/reservations/me
     */
    @GetMapping("/reservations/me")
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> myReservations(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Your reservations retrieved successfully",
                reservationService.getMyReservations(auth.getName())));
    }

    // =========================================================================
    // MANAGER APIs
    // =========================================================================

    /**
     * Manager: Cập nhật trạng thái reservation
     *
     * PATCH /api/manager/reservations/{reservationCode}/status
     */
    @PatchMapping("/manager/reservations/{reservationCode}/status")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<ReservationResponse>> updateReservationStatus(
            @PathVariable String reservationCode,
            @Valid @RequestBody UpdateReservationStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation status updated successfully",
                reservationService.updateReservationStatus(reservationCode, request.getStatus(), request.getNote())));
    }

    // =========================================================================
    // STAFF APIs - Xem danh sách reservations để approve/reject
    // =========================================================================

    /**
     * Staff: Xem TẤT CẢ reservations
     *
     * GET /api/staff/reservations
     */
    @GetMapping("/staff/reservations")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getAllReservations() {
        return ResponseEntity.ok(ApiResponse.ok(
                "All reservations retrieved successfully",
                reservationService.getAllReservations()));
    }

    /**
     * Staff: Xem reservations theo status (PENDING, APPROVED, REJECTED, CANCELLED, COMPLETED, EXPIRED)
     *
     * GET /api/staff/reservations?status=PENDING
     */
    @GetMapping("/staff/reservations/by-status")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getReservationsByStatus(
            @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservations retrieved successfully",
                reservationService.getReservationsByStatus(status)));
    }

    /**
     * Staff: Xem chi tiết 1 reservation theo ID
     *
     * GET /api/staff/reservations/{reservationId}
     */
    @GetMapping("/staff/reservations/{reservationId}")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationById(
            @PathVariable String reservationId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation retrieved successfully",
                reservationService.getReservationById(reservationId)));
    }

    /**
     * Staff: Tìm reservation theo mã (ticket code hoặc reservation code)
     *
     * GET /api/staff/reservations/code/{reservationCode}
     */
    @GetMapping("/staff/reservations/code/{reservationCode}")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationByCode(
            @PathVariable String reservationCode) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation found",
                reservationService.getReservationByCode(reservationCode)));
    }
}
