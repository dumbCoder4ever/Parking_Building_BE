package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.DashboardStatsResponse;
import fpt.swp391.parkingmanagement.service.DashboardStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Dashboard", description = "System-wide statistics, ADMIN only")
public class AdminDashboardController {

    private final DashboardStatsService dashboardStatsService;

    @Operation(
            summary = "Admin dashboard stats",
            description = "System-wide snapshot: real-time occupancy, session/reservation/user/incident stats, "
                    + "driver vs guest session counts (today, active, all-time), revenue by payment method, "
                    + "and revenue trend by date range. If fromDay/toDay are omitted, defaults to the last 7 days.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboardStats(
            @Parameter(description = "Start date (yyyy-MM-dd), defaults to 6 days ago")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDay,
            @Parameter(description = "End date (yyyy-MM-dd), defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDay) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Dashboard stats retrieved successfully",
                dashboardStatsService.getStats(fromDay, toDay)));
    }
}
