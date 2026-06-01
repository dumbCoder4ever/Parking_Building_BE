package fpt.swp391.parkingmanagement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.CreateBuildingRequest;
import fpt.swp391.parkingmanagement.dto.CreateFloorRequest;
import fpt.swp391.parkingmanagement.dto.CreateZoneRequest;
import fpt.swp391.parkingmanagement.dto.ManagerSetupResponse;
import fpt.swp391.parkingmanagement.service.ManagerBuildingSetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/manager/setup")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ManagerBuildingSetupController {

    private final ManagerBuildingSetupService managerBuildingSetupService;

    @PostMapping("/buildings")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> createBuilding(
            @Valid @RequestBody CreateBuildingRequest request) {
        ManagerSetupResponse response = managerBuildingSetupService.createBuilding(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Building created successfully", response));
    }

    @PostMapping("/buildings/{buildingId}/floors")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> createFloor(
            @PathVariable String buildingId,
            @Valid @RequestBody CreateFloorRequest request) {
        ManagerSetupResponse response = managerBuildingSetupService.createFloor(buildingId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Floor created successfully", response));
    }

    @PostMapping("/floors/{floorId}/zones")
    public ResponseEntity<ApiResponse<ManagerSetupResponse>> createZoneAndSlots(
            @PathVariable String floorId,
            @Valid @RequestBody CreateZoneRequest request) {
        ManagerSetupResponse response = managerBuildingSetupService.createZoneAndSlots(floorId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Zone and slots created successfully", response));
    }
}
