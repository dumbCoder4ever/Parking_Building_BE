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
import fpt.swp391.parkingmanagement.dto.BuildingFloorsResponse;
import fpt.swp391.parkingmanagement.dto.BuildingSummaryDto;
import fpt.swp391.parkingmanagement.dto.ZoneSlotsDto;
import fpt.swp391.parkingmanagement.service.BuildingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * =============================================================================
 * BUILDING AVAILABILITY APIs — endpoints for the availability screen
 * =============================================================================
 *
 * Mappings:
 *   GET /buildings/available        → list buildings that have free slots
 *   GET /buildings/{id}/floors      → floors + zones + slot counts for one building
 *   GET /zones/{zoneId}/slots       → slot grid for a zone
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "building-discovery", description = "Availability flow for drivers/operators. Two endpoints: (1) list available buildings, (2) get slot grid for a zone.")
public class BuildingController {

    private final BuildingService buildingService;

    // =========================================================================
    // (1) List available buildings (minimal data, fast)
    // =========================================================================
    @GetMapping("/buildings/available")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    @Operation(
            summary = "List available buildings",
            description = "Returns a lightweight list of buildings that currently have at least one free slot for the given vehicle type. Each entry exposes aggregate slot counts (free / total) so the UI can render a quick picker before drilling into a zone.")
    public ResponseEntity<ApiResponse<List<BuildingSummaryDto>>> getAvailableBuildings(
            @Parameter(description = "Filter by vehicle type id (e.g. CAR, MOTORBIKE). Optional.")
            @RequestParam(required = false) String vehicleTypeId,
            @Parameter(description = "Partial match on building name. Optional.")
            @RequestParam(required = false) String name,
            @Parameter(description = "Partial match on address. Optional.")
            @RequestParam(required = false) String address) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Available buildings retrieved successfully",
                buildingService.listAvailableBuildings(vehicleTypeId, name, address)));
    }

    // =========================================================================
    // (3) List floors of a building (with zones + slot counts)
    // =========================================================================
    @GetMapping("/buildings/{id}/floors")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    @Operation(
            summary = "List floors of a building",
            description = "Returns the ordered list of floors for a building, each with its zones and aggregate slot counts (free/reserved/occupied). Used by the availability drill-down UI.")
    public ResponseEntity<ApiResponse<BuildingFloorsResponse>> getBuildingFloors(
            @Parameter(description = "Building id") @PathVariable("id") String buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building floors retrieved successfully",
                buildingService.listFloorsOfBuilding(buildingId)));
    }

    // =========================================================================
    // (2) Zone slots: full slot grid with pricing + reservation info
    // =========================================================================
    @GetMapping("/zones/{zoneId}/slots")
    @PreAuthorize("hasAnyRole('DRIVER','STAFF','MANAGER','ADMIN')")
    @Operation(
            summary = "Get zone slot grid",
            description = "Returns the full slot grid for a zone, each slot enriched with vehicle-type, status, pricing, and an optional reservation hint (who is occupying / until when). Drives the slot-picker UI.")
    public ResponseEntity<ApiResponse<ZoneSlotsDto>> getZoneSlots(
            @Parameter(description = "Zone id") @PathVariable String zoneId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Zone slots retrieved successfully",
                buildingService.getZoneSlots(zoneId)));
    }
}