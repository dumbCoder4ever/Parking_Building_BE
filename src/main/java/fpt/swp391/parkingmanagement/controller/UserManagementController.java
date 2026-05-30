package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.UserRequest;
import fpt.swp391.parkingmanagement.dto.UserResponse;
import fpt.swp391.parkingmanagement.dto.UserUpdateRequest;
import fpt.swp391.parkingmanagement.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {
    private final UserManagementService userManagementService;

    // Create user
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse response = userManagementService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Update user
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userManagementService.updateUser(userId, request);
        return ResponseEntity.ok(response);
    }

    // Delete user (soft delete)
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userManagementService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // Get user by ID
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        UserResponse response = userManagementService.getUserById(userId);
        return ResponseEntity.ok(response);
    }

//    // Get all users with pagination
//    @GetMapping
//    public ResponseEntity<Page<UserResponse>> getAllUsers(
//            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
//        Page<UserResponse> users = userManagementService.getAllUsers(pageable);
//        return ResponseEntity.ok(users);
//    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        try {
            List<UserResponse> users = userManagementService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Search users with filters
//    @GetMapping("/search")
//    public ResponseEntity<Page<UserResponse>> searchUsers(
//            @RequestParam(required = false) String keyword,
//            @RequestParam(required = false) String role,
//            @RequestParam(required = false) String status,
//            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
//        Page<UserResponse> users = userManagementService.searchUsers(keyword, role, status, pageable);
//        return ResponseEntity.ok(users);
//    }

    // Change user status
    @PatchMapping("/{userId}/status")
    public ResponseEntity<Void> changeUserStatus(
            @PathVariable Long userId,
            @RequestParam String status) {
        userManagementService.changeUserStatus(userId, status);
        return ResponseEntity.ok().build();
    }
}
