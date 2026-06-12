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
    private final ManagerStaffService managerStaffService;

    // =========================================================================
    // FLOW 1: USER ĐẶT TRƯỚC SLOT
    // =========================================================================

    @GetMapping("/slots/availability")
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<SlotAvailabilityDto>>> getAvailableSlots(
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String vehicleTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Available slots retrieved successfully",
                reservationService.getAvailability(buildingId, vehicleTypeId)));
    }

    @PostMapping("/reservations")
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody CreateReservationRequest req,
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

    // =========================================================================
    // STAFF APIs - Cần có buildingId, staff phải được assign vào building đó
    // =========================================================================

    @GetMapping("/staff/buildings")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<StaffAssignmentResponse>>> getMyAssignedBuildings(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Assigned buildings retrieved successfully",
                managerStaffService.getBuildingsByStaff(auth.getName())));
    }

    @GetMapping("/staff/reservations")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getAllReservations(
            @RequestParam String buildingId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "All reservations retrieved successfully",
                reservationService.getAllReservations(auth.getName(), buildingId)));
    }

    @GetMapping("/staff/reservations/by-status")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getReservationsByStatus(
            @RequestParam String buildingId,
            @RequestParam String status,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservations retrieved successfully",
                reservationService.getReservationsByStatus(auth.getName(), buildingId, status)));
    }

    @GetMapping("/staff/reservations/{reservationId}")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationById(
            @PathVariable String reservationId,
            @RequestParam String buildingId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation retrieved successfully",
                reservationService.getReservationById(auth.getName(), buildingId, reservationId)));
    }

    @GetMapping("/staff/reservations/code/{reservationCode}")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationByCode(
            @PathVariable String reservationCode,
            @RequestParam String buildingId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation found",
                reservationService.getReservationByCode(auth.getName(), buildingId, reservationCode)));
    }

    @PatchMapping("/staff/reservations/{reservationCode}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<ReservationResponse>> updateReservationStatus(
            @PathVariable String reservationCode,
            @RequestParam String buildingId,
            @Valid @RequestBody UpdateReservationStatusRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation status updated successfully",
                reservationService.updateReservationStatus(
                        auth.getName(), buildingId, reservationCode, request.getStatus(), request.getNote())));
    }
}
