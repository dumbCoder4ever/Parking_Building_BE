package fpt.swp391.parkingmanagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CreateBuildingRequest;
import fpt.swp391.parkingmanagement.dto.CreateFloorRequest;
import fpt.swp391.parkingmanagement.dto.CreateZoneRequest;
import fpt.swp391.parkingmanagement.dto.ManagerSetupResponse;
import fpt.swp391.parkingmanagement.dto.UpdateBuildingRequest;
import fpt.swp391.parkingmanagement.dto.UpdateFloorRequest;
import fpt.swp391.parkingmanagement.dto.UpdateSetupStatusRequest;
import fpt.swp391.parkingmanagement.dto.UpdateZoneRequest;
import fpt.swp391.parkingmanagement.service.ManagerBuildingSetupService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/manager/setup")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ManagerBuildingSetupController {

    private final ManagerBuildingSetupService managerBuildingSetupService;

    // --- Buildings ---

    @Operation(summary = "List buildings")
    @GetMapping("/buildings")
    public ResponseEntity<ApiResponse<List<ManagerSetupResponse>>> getAllBuildings() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Buildings retrieved successfully",
                managerBuildingSetupService.getAllBuildings()));
    }

    @Operation(summary = "Create building")
    @PostMapping("/buildings")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> createBuilding(
            @Valid @RequestBody CreateBuildingRequest request) {
        ManagerSetupResponse response = managerBuildingSetupService.createBuilding(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Building created successfully", response));
    }

    @Operation(summary = "Get building by id")
    @GetMapping("/buildings/{buildingId}")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> getBuilding(@PathVariable String buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building retrieved successfully",
                managerBuildingSetupService.getBuilding(buildingId)));
    }

    @Operation(summary = "Update building")
    @PutMapping("/buildings/{buildingId}")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> updateBuilding(
            @PathVariable String buildingId,
            @Valid @RequestBody UpdateBuildingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building updated successfully",
                managerBuildingSetupService.updateBuilding(buildingId, request)));
    }

    @Operation(summary = "Update building status")
    @PatchMapping("/buildings/{buildingId}/status")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> updateBuildingStatus(
            @PathVariable String buildingId,
            @Valid @RequestBody UpdateSetupStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building status updated successfully",
                managerBuildingSetupService.updateBuildingStatus(buildingId, request.getStatus())));
    }

    // --- Floors ---

    @Operation(summary = "List floors in building")
    @GetMapping("/buildings/{buildingId}/floors")
    public ResponseEntity<ApiResponse<List<ManagerSetupResponse>>> getFloorsByBuilding(
            @PathVariable String buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Floors retrieved successfully",
                managerBuildingSetupService.getFloorsByBuilding(buildingId)));
    }

    @Operation(
            summary = "Create floor",
            description = "Each floor serves one vehicle type. "
                    + "Set vehicleTypeId from GET /api/vehicles/types. "
                    + "Do not use buildingId from the URL path.")
    @PostMapping("/buildings/{buildingId}/floors")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> createFloor(
            @PathVariable String buildingId,
            @Valid @RequestBody CreateFloorRequest request) {
        ManagerSetupResponse response = managerBuildingSetupService.createFloor(buildingId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Floor created successfully", response));
    }

    @Operation(
            summary = "Update floor",
            description = "Update floor name, max capacity, and vehicle type. "
                    + "Set vehicleTypeId from GET /api/vehicles/types.")
    @PutMapping("/floors/{floorId}")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> updateFloor(
            @PathVariable String floorId,
            @Valid @RequestBody UpdateFloorRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Floor updated successfully",
                managerBuildingSetupService.updateFloor(floorId, request)));
    }

    @Operation(summary = "Update floor status")
    @PatchMapping("/floors/{floorId}/status")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> updateFloorStatus(
            @PathVariable String floorId,
            @Valid @RequestBody UpdateSetupStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Floor status updated successfully",
                managerBuildingSetupService.updateFloorStatus(floorId, request.getStatus())));
    }

    // --- Zones ---

    @Operation(summary = "List zones on floor")
    @GetMapping("/floors/{floorId}/zones")
    public ResponseEntity<ApiResponse<List<ManagerSetupResponse>>> getZonesByFloor(@PathVariable String floorId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Zones retrieved successfully",
                managerBuildingSetupService.getZonesByFloor(floorId)));
    }

    @Operation(summary = "Create zone and slots")
    @PostMapping("/floors/{floorId}/zones")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> createZoneAndSlots(
            @PathVariable String floorId,
            @Valid @RequestBody CreateZoneRequest request) {
        ManagerSetupResponse response = managerBuildingSetupService.createZoneAndSlots(floorId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Zone and slots created successfully", response));
    }

    @Operation(
            summary = "Update zone",
            description = "Update zone name and change the number of slots by adjusting maxCapacity.")
    @PutMapping("/zones/{zoneId}")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> updateZone(
            @PathVariable String zoneId,
            @Valid @RequestBody UpdateZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Zone updated successfully",
                managerBuildingSetupService.updateZone(zoneId, request)));
    }

    @Operation(summary = "Update zone status")
    @PatchMapping("/zones/{zoneId}/status")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> updateZoneStatus(
            @PathVariable String zoneId,
            @Valid @RequestBody UpdateSetupStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Zone status updated successfully",
                managerBuildingSetupService.updateZoneStatus(zoneId, request.getStatus())));
    }

    // --- Slots ---

    @Operation(summary = "List slots in zone")
    @GetMapping("/zones/{zoneId}/slots")
    public ResponseEntity<ApiResponse<List<ManagerSetupResponse>>> getSlotsByZone(@PathVariable String zoneId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Parking slots retrieved successfully",
                managerBuildingSetupService.getSlotsByZone(zoneId)));
    }
}
