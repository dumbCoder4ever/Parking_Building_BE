package fpt.swp391.parkingmanagement.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.DashboardStatsResponse;
import fpt.swp391.parkingmanagement.dto.DriverSummaryResponse;
import fpt.swp391.parkingmanagement.dto.PeakHourAnalysisResponse;
import fpt.swp391.parkingmanagement.dto.RevenueDashboardResponse;
import fpt.swp391.parkingmanagement.dto.SlotSuggestionResponse;
import fpt.swp391.parkingmanagement.dto.TransferVehicleOwnerRequest;
import fpt.swp391.parkingmanagement.dto.UpdateVehicleStatusRequest;
import fpt.swp391.parkingmanagement.dto.VehicleResponse;
import fpt.swp391.parkingmanagement.service.DashboardStatsService;
import fpt.swp391.parkingmanagement.service.ManagerDriverService;
import fpt.swp391.parkingmanagement.service.PeakHourService;
import fpt.swp391.parkingmanagement.service.RevenueDashboardService;
import fpt.swp391.parkingmanagement.service.SlotSuggestionService;
import fpt.swp391.parkingmanagement.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ManagerController {

    private final ManagerDriverService managerDriverService;
    private final VehicleService vehicleService;
    private final RevenueDashboardService revenueDashboardService;
    private final DashboardStatsService dashboardStatsService;
    private final PeakHourService peakHourService;
    private final SlotSuggestionService slotSuggestionService;

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
            description = "Returns all vehicles with owner info and latest check-in/check-out times in one call. "
                    + "Supports optional filters: plateNumber, status, userId, username, ownerFullName, "
                    + "vehicleTypeId, parked, checkInFrom, checkInTo.")
    @GetMapping("/vehicles")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getAllVehicles(
            @Parameter(description = "Partial match on plate number, e.g. \"30\" matches \"30A-12345\"")
            @RequestParam(required = false) String plateNumber,
            @Parameter(description = "Vehicle status: ACTIVE, INACTIVE, BLOCKED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Owner driver userId")
            @RequestParam(required = false) String userId,
            @Parameter(description = "Partial match on owner username")
            @RequestParam(required = false) String username,
            @Parameter(description = "Partial match on owner full name")
            @RequestParam(required = false) String ownerFullName,
            @Parameter(description = "Vehicle type id")
            @RequestParam(required = false) String vehicleTypeId,
            @Parameter(description = "true = currently parked, false = not in parking lot")
            @RequestParam(required = false) Boolean parked,
            @Parameter(description = "Filter by check-in time from (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkInFrom,
            @Parameter(description = "Filter by check-in time to (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkInTo) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicles retrieved successfully",
                vehicleService.searchVehicles(
                        plateNumber, status, userId, username, ownerFullName,
                        vehicleTypeId, parked, checkInFrom, checkInTo)));
    }

    @Operation(summary = "Transfer vehicle to a new owner", description = "Used when a vehicle is sold to another driver.")
    @PatchMapping("/vehicles/{vehicleId}/owner")
    public ResponseEntity<ApiResponse<VehicleResponse>> transferVehicleOwner(
            @PathVariable String vehicleId,
            @Valid @RequestBody TransferVehicleOwnerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vehicle owner updated successfully",
                vehicleService.transferVehicleOwner(vehicleId, request.getNewUserId())));
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

    @Operation(
            summary = "Revenue dashboard",
            description = "Returns total revenue and revenue breakdown by building. "
                    + "Only counts successful payments (PAID, CONFIRMED, SUCCESS). "
                    + "Optional from/to filters apply to paymentTime. "
                    + "Optional buildingId scopes totals and the buildings list to that building.")
    @GetMapping("/dashboard/revenue")
    public ResponseEntity<ApiResponse<RevenueDashboardResponse>> getRevenueDashboard(
            @Parameter(description = "Filter from payment time (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Filter to payment time (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Optional building filter")
            @RequestParam(required = false) String buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Revenue dashboard retrieved successfully",
                revenueDashboardService.getRevenueDashboard(from, to, buildingId)));
    }

    @Operation(
            summary = "Manager dashboard stats",
            description = "Occupancy, sessions, reservations, users, incidents, revenue trend. "
                    + "Optional buildingId filters occupancy, sessions, reservations, incidents, revenue, "
                    + "driversCurrentlyParked and staff count. totalDrivers/totalManagers/newUsersThisMonth stay system-wide. "
                    + "Incident stats exclude auto-created SYSTEM reports (driver/staff incidents only).")
    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDay,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDay,
            @RequestParam(required = false) String buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Dashboard stats retrieved successfully",
                dashboardStatsService.getStats(fromDay, toDay, buildingId)));
    }

    @Operation(summary = "Peak hour analysis", description = "Hourly check-in distribution and peak hours.")
    @GetMapping("/dashboard/peak-hours")
    public ResponseEntity<ApiResponse<PeakHourAnalysisResponse>> getPeakHours(
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDay,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDay) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Peak hour analysis retrieved successfully",
                peakHourService.analyze(buildingId, fromDay, toDay)));
    }

    @Operation(
            summary = "Heuristic slot suggestions",
            description = "Suggest available slots by lower floor and lower zone occupancy.")
    @GetMapping("/analytics/slot-suggestion")
    public ResponseEntity<ApiResponse<List<SlotSuggestionResponse>>> suggestSlots(
            @Parameter(description = "Building ID", example = "") @RequestParam String buildingId,
            @Parameter(description = "Vehicle type ID", example = "") @RequestParam String vehicleTypeId,
            @Parameter(description = "Number of suggested slots (default 5, max 20)") @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Slot suggestions retrieved successfully",
                slotSuggestionService.suggest(buildingId, vehicleTypeId, limit)));
    }

}
