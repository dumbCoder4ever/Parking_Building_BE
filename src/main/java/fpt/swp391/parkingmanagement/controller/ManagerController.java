package fpt.swp391.parkingmanagement.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.DriverSummaryResponse;
import fpt.swp391.parkingmanagement.dto.UpdateVehicleStatusRequest;
import fpt.swp391.parkingmanagement.dto.VehicleResponse;
import fpt.swp391.parkingmanagement.service.ManagerDriverService;
import fpt.swp391.parkingmanagement.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ManagerController {

    private final ManagerDriverService managerDriverService;
    private final VehicleService vehicleService;

    @Operation(summary = "Get all drivers", description = "Returns drivers for manager to select and view their vehicles.")
    @GetMapping("/drivers")
    public ResponseEntity<ApiResponse<List<DriverSummaryResponse>>> getAllDrivers() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Drivers retrieved successfully",
                managerDriverService.getAllDrivers()));
    }

    @Operation(summary = "Get vehicles by driver", description = "Pass userId from the driver list to load that driver's vehicles.")
    @GetMapping("/drivers/{userId}/vehicles")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getDriverVehicles(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicles retrieved successfully",
                managerDriverService.getDriverVehicles(userId)));
    }

    @Operation(summary = "Get vehicles by driver username", description = "Look up a driver's vehicles using their username.")
    @GetMapping("/drivers/by-username/{username}/vehicles")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getDriverVehiclesByUsername(
            @PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicles retrieved successfully",
                managerDriverService.getDriverVehiclesByUsername(username)));
    }

    @Operation(
            summary = "Get all vehicles",
            description = "Returns all vehicles with owner username. "
                    + "Optional plateNumber supports partial match (e.g. \"30\" matches \"30A - 12345\" and \"30A - 24567\").")
    @GetMapping("/vehicles")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getAllVehicles(
            @RequestParam(required = false) String plateNumber) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicles retrieved successfully",
                vehicleService.searchVehicles(plateNumber)));
    }

    @Operation(summary = "Update vehicle status", description = "Allowed values: ACTIVE, INACTIVE, BLOCKED")
    @PatchMapping("/vehicles/{vehicleId}/status")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateVehicleStatus(
            @PathVariable String vehicleId,
            @Valid @RequestBody UpdateVehicleStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle status updated successfully",
                vehicleService.updateVehicleStatus(vehicleId, request.getStatus())));
    }
}
