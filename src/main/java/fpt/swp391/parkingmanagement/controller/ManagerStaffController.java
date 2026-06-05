package fpt.swp391.parkingmanagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.AssignStaffBuildingsRequest;
import fpt.swp391.parkingmanagement.dto.AssignStaffRequest;
import fpt.swp391.parkingmanagement.dto.StaffAssignmentResponse;
import fpt.swp391.parkingmanagement.dto.StaffSummaryResponse;
import fpt.swp391.parkingmanagement.service.ManagerStaffService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ManagerStaffController {

    private final ManagerStaffService managerStaffService;

    @Operation(
            summary = "List staff users",
            description = "Returns users with ROLE_STAFF. Use userId when assigning staff to buildings.")
    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<StaffSummaryResponse>>> getAllStaff() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Staff users retrieved successfully",
                managerStaffService.getAllStaff()));
    }

    @Operation(
            summary = "List buildings assigned to staff",
            description = "A staff user can be assigned to multiple buildings.")
    @GetMapping("/staff/{userId}/buildings")
    public ResponseEntity<ApiResponse<List<StaffAssignmentResponse>>> getBuildingsByStaff(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Staff building assignments retrieved successfully",
                managerStaffService.getBuildingsByStaff(userId)));
    }

    @Operation(
            summary = "Assign staff to multiple buildings",
            description = "Replaces all existing building assignments for this staff user.")
    @PutMapping("/staff/{userId}/buildings")
    public ResponseEntity<ApiResponse<List<StaffAssignmentResponse>>> assignStaffToBuildings(
            @PathVariable String userId,
            @Valid @RequestBody AssignStaffBuildingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Staff building assignments updated successfully",
                managerStaffService.assignStaffToBuildings(userId, request)));
    }

    @Operation(summary = "List staff in building")
    @GetMapping("/buildings/{buildingId}/staff")
    public ResponseEntity<ApiResponse<List<StaffAssignmentResponse>>> getStaffByBuilding(
            @PathVariable String buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building staff retrieved successfully",
                managerStaffService.getStaffByBuilding(buildingId)));
    }

    @Operation(
            summary = "Add staff to building",
            description = "Assign one ROLE_STAFF user to a building. Staff can belong to multiple buildings.")
    @PostMapping("/buildings/{buildingId}/staff")
    public ResponseEntity<ApiResponse<StaffAssignmentResponse>> assignStaffToBuilding(
            @PathVariable String buildingId,
            @Valid @RequestBody AssignStaffRequest request) {
        StaffAssignmentResponse response = managerStaffService.assignStaffToBuilding(buildingId, request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Staff assigned to building successfully", response));
    }

    @Operation(summary = "Remove staff from building")
    @DeleteMapping("/buildings/{buildingId}/staff/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeStaffFromBuilding(
            @PathVariable String buildingId,
            @PathVariable String userId) {
        managerStaffService.removeStaffFromBuilding(buildingId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Staff removed from building successfully", null));
    }
}
