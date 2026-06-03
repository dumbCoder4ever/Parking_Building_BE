package fpt.swp391.parkingmanagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CreateVehicleRequest;
import fpt.swp391.parkingmanagement.dto.UpdateVehicleRequest;
import fpt.swp391.parkingmanagement.dto.UpdateVehicleStatusRequest;
import fpt.swp391.parkingmanagement.dto.VehicleResponse;
import fpt.swp391.parkingmanagement.dto.VehicleTypeOptionResponse;
import fpt.swp391.parkingmanagement.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "List vehicle types", description = "Use vehicleTypeId when registering a vehicle.")
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<VehicleTypeOptionResponse>>> getVehicleTypes() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle types retrieved successfully",
                vehicleService.getVehicleTypeOptions()));
    }

    @Operation(summary = "List my vehicles")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getMyVehicles(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicles retrieved successfully",
                vehicleService.getMyVehicles(auth.getName())));
    }

    @Operation(summary = "Register a vehicle")
    @PostMapping("/me")
    public ResponseEntity<ApiResponse<VehicleResponse>> createMyVehicle(
            Authentication auth,
            @Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse response = vehicleService.createMyVehicle(auth.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Vehicle registered successfully", response));
    }

    @Operation(summary = "Get my vehicle by id")
    @GetMapping("/me/{vehicleId}")
    public ResponseEntity<ApiResponse<VehicleResponse>> getMyVehicle(
            Authentication auth,
            @PathVariable String vehicleId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle retrieved successfully",
                vehicleService.getMyVehicle(auth.getName(), vehicleId)));
    }

    @Operation(summary = "Update my vehicle")
    @PutMapping("/me/{vehicleId}")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateMyVehicle(
            Authentication auth,
            @PathVariable String vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle updated successfully",
                vehicleService.updateMyVehicle(auth.getName(), vehicleId, request)));
    }

    @Operation(summary = "Update my vehicle status", description = "Allowed values: ACTIVE, INACTIVE")
    @PatchMapping("/me/{vehicleId}/status")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateMyVehicleStatus(
            Authentication auth,
            @PathVariable String vehicleId,
            @Valid @RequestBody UpdateVehicleStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle status updated successfully",
                vehicleService.updateMyVehicleStatus(auth.getName(), vehicleId, request.getStatus())));
    }

    @Operation(summary = "Delete my vehicle")
    @DeleteMapping("/me/{vehicleId}")
    public ResponseEntity<ApiResponse<Void>> deleteMyVehicle(
            Authentication auth,
            @PathVariable String vehicleId) {
        vehicleService.deleteMyVehicle(auth.getName(), vehicleId);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle deleted successfully", null));
    }
}
