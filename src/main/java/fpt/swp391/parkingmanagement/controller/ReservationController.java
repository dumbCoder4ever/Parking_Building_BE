package fpt.swp391.parkingmanagement.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CancelReservationRequest;
import fpt.swp391.parkingmanagement.dto.CreateReservationRequest;
import fpt.swp391.parkingmanagement.dto.ReservationResponse;
import fpt.swp391.parkingmanagement.dto.SlotAvailabilityDto;
import fpt.swp391.parkingmanagement.dto.StaffAssignmentResponse;
import fpt.swp391.parkingmanagement.dto.UpdateReservationStatusRequest;
import fpt.swp391.parkingmanagement.service.ManagerStaffService;
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
 * Bước 3: Nhập thông tin xe (biển số, màu, hãng, model, loại xe) + upload ảnh xe (optional)
 * Bước 4: Chọn thời gian gửi (start -> end)
 * Bước 5: Tạo Reservation -> Slot: AVAILABLE -> RESERVED
 * Bước 6: Sinh Ticket (ticket_code)
 *
 * FLOW 2 — STAFF XÁC NHẬN XE VÀO BÃI:
 * Bước 1: User đến bãi xe (đưa ticket, biển số)
 * Bước 2: Staff kiểm tra (ticket, biển số, loại xe, màu xe)
 * Bước 3: Nếu hợp lệ -> tạo Parking Session, Slot: RESERVED -> OCCUPIED
 * Bước 4: Nếu không hợp lệ -> Staff từ chối check-in
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ManagerStaffService managerStaffService;

    // =========================================================================
    // FLOW 1: USER ĐẶT TRƯỚC SLOT
    // =========================================================================

    @GetMapping("/slots/availability")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<SlotAvailabilityDto>>> getAvailableSlots(
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String vehicleTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Available slots retrieved successfully",
                reservationService.getAvailability(buildingId, vehicleTypeId)));
    }

    @PostMapping(value = "/reservations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservationJson(
            @Valid @RequestBody CreateReservationRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation created successfully. Please show your ticket when checking in.",
                reservationService.createReservation(auth.getName(), req)));
    }

    @PostMapping(value = "/reservations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservationMultipart(
            @Valid @ModelAttribute CreateReservationRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation created successfully. Please show your ticket when checking in.",
                reservationService.createReservation(auth.getName(), req)));
    }

    @GetMapping("/reservations/me")
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> myReservations(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Your reservations retrieved successfully",
                reservationService.getMyReservations(auth.getName())));
    }

    /**
     * Driver hủy reservation của chính mình.
     * Chỉ hủy được khi reservation status = PENDING (chưa checkin).
     */
    @PostMapping("/reservations/{reservationCode}/cancel")
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelMyReservation(
            @PathVariable String reservationCode,
            @Valid @RequestBody(required = false) CancelReservationRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation cancelled successfully",
                reservationService.cancelReservationByDriver(auth.getName(), reservationCode, req)));
    }

    // =========================================================================
    // STAFF APIs - Cần có buildingId, staff phải được assign vào building đó
    // =========================================================================

    @GetMapping("/staff/buildings")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<StaffAssignmentResponse>>> getMyAssignedBuildings(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Assigned buildings retrieved successfully",
                managerStaffService.getBuildingsByEmail(auth.getName())));
    }

    @GetMapping("/staff/reservations")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Page<ReservationResponse>>> getAllReservations(
            Authentication auth,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                "All reservations retrieved successfully",
                reservationService.getAllReservationsForStaffPaginated(auth.getName(), pageable)));
    }

    @GetMapping("/staff/reservations/queue")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getPendingQueue(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Pending reservations in FIFO order",
                reservationService.getPendingReservationsFifo(auth.getName())));
    }

    @GetMapping("/staff/reservations/by-status")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getReservationsByStatus(
            @RequestParam String status,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservations retrieved successfully",
                reservationService.getReservationsByStatusForStaff(auth.getName(), status)));
    }

    @GetMapping("/staff/reservations/pending/by-building/{buildingId}")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getPendingReservationsByBuilding(
            @PathVariable String buildingId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Pending reservations retrieved successfully",
                reservationService.getPendingReservationsByBuilding(auth.getName(), buildingId)));
    }

    /**
     * Tìm reservation PENDING/APPROVED theo biển số xe.
     * Dùng khi staff check-in bằng OCR: nhận diện biển số → tìm reservation của driver.
     */
    @GetMapping("/staff/reservations/by-plate")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getReservationsByPlate(
            @RequestParam String plateNumber,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservations found for plate",
                reservationService.findReservationsByPlateNumber(auth.getName(), plateNumber)));
    }

    @GetMapping("/staff/reservations/code/{reservationCode}")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationByCode(
            @PathVariable String reservationCode,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation found",
                reservationService.getReservationByCodeForStaff(auth.getName(), reservationCode)));
    }

    @PatchMapping("/staff/reservations/{reservationCode}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> updateReservationStatus(
            @PathVariable String reservationCode,
            @Valid @RequestBody UpdateReservationStatusRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation status updated successfully",
                reservationService.updateReservationStatusForStaff(
                        auth.getName(), reservationCode, request.getStatus(), request.getNote())));
    }

    /**
     * Staff hủy reservation giúp driver.
     * Staff có thể hủy PENDING (chưa checkin) hoặc CHECKED_IN (đã checkin nhưng phải checkout trước).
     * Staff chỉ hủy được reservation thuộc building mình được assign.
     */
    @PostMapping("/staff/reservations/{reservationCode}/cancel")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservationByStaff(
            @PathVariable String reservationCode,
            @Valid @RequestBody(required = false) CancelReservationRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation cancelled successfully",
                reservationService.cancelReservationByStaff(auth.getName(), reservationCode, req)));
    }
}
