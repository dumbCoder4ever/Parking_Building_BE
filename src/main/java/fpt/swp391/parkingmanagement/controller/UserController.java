package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.*;
import fpt.swp391.parkingmanagement.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ─── Current User (me) ───────────────────────────────────────────────────

    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyProfile(auth.getName())));
    }

    @PutMapping(value = "/users/me" ,    consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            Authentication auth,
            @RequestPart("data") UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", userService.updateMyProfile(auth.getName(), request)));
    }

    @PutMapping("/users/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication auth,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(auth.getName(), request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
    }

    // ─── Admin: User Management ───────────────────────────────────────────────

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userService.getAllUsers()));
    }

    @GetMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(userId)));
    }

    @PutMapping("/admin/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("User status updated", userService.updateUserStatus(userId, request)));
    }

    @PutMapping("/admin/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserRole(
            @PathVariable String userId,
            @RequestParam String role) {
        return ResponseEntity.ok(ApiResponse.ok("User role updated", userService.updateUserRole(userId, role)));
    }

}
