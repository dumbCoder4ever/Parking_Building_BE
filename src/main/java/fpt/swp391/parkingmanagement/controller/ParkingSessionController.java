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
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ParkingSessionController {

    private final ParkingSessionService parkingSessionService;

    @PostMapping("/sessions/checkin")
    public ResponseEntity<ApiResponse<ParkingSessionResponse>> checkin(@RequestBody CheckinRequest req, Authentication auth) {
        ParkingSessionResponse resp = parkingSessionService.checkin(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Check-in successful", resp));
    }

    @PostMapping("/sessions/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(@RequestBody CheckoutRequest req, Authentication auth) {
        CheckoutResponse resp = parkingSessionService.checkout(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.ok("Checkout successful", resp));
    }
}
