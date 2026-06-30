package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.DashboardStatsResponse;
import fpt.swp391.parkingmanagement.service.DashboardStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Dashboard", description = "Thống kê tổng hợp toàn hệ thống, chỉ dành cho ADMIN")
public class AdminDashboardController {

    private final DashboardStatsService dashboardStatsService;

    @Operation(
            summary = "Admin dashboard stats",
            description = "Snapshot tổng hợp toàn bộ hệ thống: tình trạng chỗ đỗ thời gian thực, "
                    + "thống kê session/reservation/user/incident, doanh thu theo phương thức thanh toán "
                    + "và trend doanh thu 7 ngày gần nhất.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboardStats() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Dashboard stats retrieved successfully",
                dashboardStatsService.getStats()));
    }
}
