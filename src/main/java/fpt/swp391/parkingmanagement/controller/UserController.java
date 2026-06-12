package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.*;
import fpt.swp391.parkingmanagement.service.DriverService;
import fpt.swp391.parkingmanagement.service.UserService;
import fpt.swp391.parkingmanagement.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserManagementService userManagementService;
    private final DriverService driverService;

    // ─── Current User Profile ─────────────────────────────────────────────────

    @GetMapping("/users/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyProfile(auth.getName())));
    }

    @PutMapping(value = "/users/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            Authentication auth,
            @ModelAttribute UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", userService.updateMyProfile(auth.getName(), request)));
    }

    @PutMapping("/users/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication auth,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(auth.getName(), request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
    }

    // ─── Driver: My Vehicles ───────────────────────────────────────────────────

    @GetMapping("/users/me/vehicles")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getMyVehicles(Authentication auth) {
        List<VehicleResponse> vehicles = driverService.getMyVehicles(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Vehicles retrieved successfully", vehicles));
    }

    @PostMapping("/users/me/vehicles")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<VehicleResponse>> addMyVehicle(
            Authentication auth,
            @Valid @RequestBody VehicleRequest request) {
        VehicleResponse vehicle = driverService.addVehicle(auth.getName(), request);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle added successfully", vehicle));
    }

    @PutMapping("/users/me/vehicles/{vehicleId}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateMyVehicle(
            Authentication auth,
            @PathVariable String vehicleId,
            @Valid @RequestBody VehicleRequest request) {
        VehicleResponse vehicle = driverService.updateVehicle(auth.getName(), vehicleId, request);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle updated successfully", vehicle));
    }

    @DeleteMapping("/users/me/vehicles/{vehicleId}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<Void>> deleteMyVehicle(
            Authentication auth,
            @PathVariable String vehicleId) {
        driverService.deleteVehicle(auth.getName(), vehicleId);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle deleted successfully", null));
    }

    // ─── Driver: My Sessions & Stats ───────────────────────────────────────────

    @GetMapping("/users/me/sessions")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<List<DriverSessionHistoryResponse>>> getMyParkingHistory(
            Authentication auth,
            @RequestParam(defaultValue = "20") int limit) {
        List<DriverSessionHistoryResponse> history = driverService.getMyParkingHistory(auth.getName(), limit);
        return ResponseEntity.ok(ApiResponse.ok("Parking history retrieved successfully", history));
    }

    @GetMapping("/users/me/sessions/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DriverCurrentSessionResponse>> getMyCurrentSession(Authentication auth) {
        DriverCurrentSessionResponse session = driverService.getMyCurrentSession(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Current parking session retrieved successfully", session));
    }

    @GetMapping("/users/me/payments")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPaymentHistory(
            Authentication auth,
            @RequestParam(defaultValue = "20") int limit) {
        List<PaymentResponse> payments = driverService.getMyPaymentHistory(auth.getName(), limit);
        return ResponseEntity.ok(ApiResponse.ok("Payment history retrieved successfully", payments));
    }

    @GetMapping("/users/me/stats")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverStatsResponse>> getMyStats(Authentication auth) {
        DriverStatsResponse stats = driverService.getDriverStats(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Stats retrieved successfully", stats));
    }

    // ─── Admin: User Management ───────────────────────────────────────────────

    @PostMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse response = userManagementService.createUser(request);
        return ResponseEntity.status(201).body(ApiResponse.ok("User created successfully", response));
    }

    @PatchMapping("/admin/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> changeUserRole(
            @PathVariable String userId,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("User role updated successfully",
                userManagementService.changeUserRole(userId, request)));
    }

    @DeleteMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String userId) {
        userManagementService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("User deleted successfully", null));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAdminUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userManagementService.getAllUsers()));
    }

    @GetMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getAdminUserById(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok(userManagementService.getUserById(userId)));
    }

    @PatchMapping("/admin/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changeUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        userManagementService.changeUserStatus(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("User status changed successfully", null));
    }

}
