package fpt.swp391.parkingmanagement.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.BuildingDetailDto;
import fpt.swp391.parkingmanagement.dto.BuildingFloorsDto;
import fpt.swp391.parkingmanagement.dto.BuildingInfoDto;
import fpt.swp391.parkingmanagement.dto.BuildingSummaryDto;
import fpt.swp391.parkingmanagement.dto.ZoneSlotsDto;
import fpt.swp391.parkingmanagement.service.BuildingService;
import lombok.RequiredArgsConstructor;

/**
 * =============================================================================
 * BUILDING AVAILABILITY APIs — 3-level progressive loading
 * =============================================================================
 *
 * UX Flow:
 *   Step 1: GET /buildings/available      → list buildings with slot counts
 *   Step 2a: GET /buildings/{id}/info     → building info + pricing + slots (fast, loads first)
 *   Step 2b: GET /buildings/{id}/floors  → floors + zones (loads second)
 *   Step 3: GET /zones/{id}/slots         → slot grid with pricing + reservation info
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    // =========================================================================
    // STEP 1 — List available buildings (minimal data, fast)
    // =========================================================================
    @GetMapping("/buildings/available")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<BuildingSummaryDto>>> getAvailableBuildings(
            @RequestParam(required = false) String vehicleTypeId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String address) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Available buildings retrieved successfully",
                buildingService.listAvailableBuildings(vehicleTypeId, name, address)));
    }

    // =========================================================================
    // STEP 2a — Building info: pricing + slot counts (no floors)
    // =========================================================================
    @GetMapping("/buildings/{buildingId}/info")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<BuildingInfoDto>> getBuildingInfo(
            @PathVariable String buildingId,
            @RequestParam(required = false) String vehicleTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building info retrieved successfully",
                buildingService.getBuildingInfo(buildingId, vehicleTypeId)));
    }

    // =========================================================================
    // STEP 2b — Building floors: floors + zones with slot counts (no building info)
    // =========================================================================
    @GetMapping("/buildings/{buildingId}/floors")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<BuildingFloorsDto>> getBuildingFloors(
            @PathVariable String buildingId,
            @RequestParam(required = false) String vehicleTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building floors retrieved successfully",
                buildingService.getBuildingFloors(buildingId, vehicleTypeId)));
    }

    // =========================================================================
    // LEGACY — Full building detail (kept for backward compatibility)
    // =========================================================================
    @GetMapping("/buildings/{buildingId}/detail")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<BuildingDetailDto>> getBuildingDetail(
            @PathVariable String buildingId,
            @RequestParam(required = false) String vehicleTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building detail retrieved successfully",
                buildingService.getBuildingDetail(buildingId, vehicleTypeId)));
    }

    // =========================================================================
    // STEP 3 — Zone slots: full slot grid with pricing + reservation info
    // =========================================================================
    @GetMapping("/zones/{zoneId}/slots")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ZoneSlotsDto>> getZoneSlots(@PathVariable String zoneId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Zone slots retrieved successfully",
                buildingService.getZoneSlots(zoneId)));
    }
}
