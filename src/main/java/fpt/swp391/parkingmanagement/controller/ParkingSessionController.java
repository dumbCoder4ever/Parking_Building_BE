package fpt.swp391.parkingmanagement.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CheckinRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.ParkingSessionResponse;
import fpt.swp391.parkingmanagement.service.ParkingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
public class ParkingSessionController {

    private final ParkingSessionService parkingSessionService;

    @Operation(summary = "Staff Check-in", description = "Staff quét vé để cho xe vào bãi. Tạo ParkingSession, đánh dấu slot OCCUPIED.")
    @PostMapping("/sessions/checkin")
    public ResponseEntity<ApiResponse<ParkingSessionResponse>> checkin(
            @Valid @RequestBody CheckinRequest req,
            Authentication auth) {
        ParkingSessionResponse resp = parkingSessionService.checkin(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Check-in successful", resp));
    }

    @Operation(summary = "Staff Check-out", description = "Staff quét vé khi xe ra. Tính phí, giải phóng slot, đánh dấu reservation COMPLETED.")
    @PostMapping("/sessions/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @Valid @RequestBody CheckoutRequest req,
            Authentication auth) {
        CheckoutResponse resp = parkingSessionService.checkout(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Checkout successful", resp));
    }
}
