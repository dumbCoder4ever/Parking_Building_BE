package fpt.swp391.parkingmanagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import fpt.swp391.parkingmanagement.dto.CreateVehicleTypeRequest;
import fpt.swp391.parkingmanagement.dto.UpdateVehicleRequest;
import fpt.swp391.parkingmanagement.dto.UpdateVehicleTypeRequest;
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

    @Operation(
            summary = "List vehicle types",
            description = "Use vehicleTypeId when registering a vehicle or creating a floor.")
    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<VehicleTypeOptionResponse>>> getVehicleTypes() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle types retrieved successfully",
                vehicleService.getVehicleTypeOptions()));
    }

    @Operation(summary = "Get vehicle type by id")
    @GetMapping("/types/{vehicleTypeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VehicleTypeOptionResponse>> getVehicleType(
            @PathVariable String vehicleTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle type retrieved successfully",
                vehicleService.getVehicleType(vehicleTypeId)));
    }

    @Operation(
            summary = "Create vehicle type",
            description = "Manager creates a vehicle type such as Motorbike, Car, SUV.")
    @PostMapping("/types")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<VehicleTypeOptionResponse>> createVehicleType(
            @Valid @RequestBody CreateVehicleTypeRequest request) {
        VehicleTypeOptionResponse response = vehicleService.createVehicleType(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Vehicle type created successfully", response));
    }

    @Operation(summary = "Update vehicle type")
    @PutMapping("/types/{vehicleTypeId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<VehicleTypeOptionResponse>> updateVehicleType(
            @PathVariable String vehicleTypeId,
            @Valid @RequestBody UpdateVehicleTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle type updated successfully",
                vehicleService.updateVehicleType(vehicleTypeId, request)));
    }

    @Operation(
            summary = "Delete vehicle type",
            description = "Fails if the type is used by floors, vehicles, or pricing policies.")
    @DeleteMapping("/types/{vehicleTypeId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteVehicleType(@PathVariable String vehicleTypeId) {
        vehicleService.deleteVehicleType(vehicleTypeId);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle type deleted successfully", null));
    }

    @Operation(summary = "List my vehicles")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getMyVehicles(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicles retrieved successfully",
                vehicleService.getMyVehicles(auth.getName())));
    }

    @Operation(summary = "Register a vehicle")
    @PostMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VehicleResponse>> createMyVehicle(
            Authentication auth,
            @Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse response = vehicleService.createMyVehicle(auth.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Vehicle registered successfully", response));
    }

    @Operation(summary = "Get my vehicle by id")
    @GetMapping("/me/{vehicleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VehicleResponse>> getMyVehicle(
            Authentication auth,
            @PathVariable String vehicleId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle retrieved successfully",
                vehicleService.getMyVehicle(auth.getName(), vehicleId)));
    }

    @Operation(summary = "Update my vehicle")
    @PutMapping("/me/{vehicleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateMyVehicle(
            Authentication auth,
            @PathVariable String vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle updated successfully",
                vehicleService.updateMyVehicle(auth.getName(), vehicleId, request)));
    }

    @Operation(summary = "Delete my vehicle")
    @DeleteMapping("/me/{vehicleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteMyVehicle(
            Authentication auth,
            @PathVariable String vehicleId) {
        vehicleService.deleteMyVehicle(auth.getName(), vehicleId);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle deleted successfully", null));
    }
}
