package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    /** Thời điểm snapshot được tạo */
    private LocalDateTime generatedAt;
    /** Tình trạng chỗ đỗ theo thời gian thực */
    private OccupancyStatsResponse occupancy;
    /** Thống kê phiên đỗ xe */
    private SessionStatsResponse sessions;
    /** Thống kê đặt chỗ trước */
    private ReservationStatsResponse reservations;
    /** Thống kê người dùng */
    private UserStatsResponse users;
    /** Thống kê sự cố */
    private IncidentStatsResponse incidents;
    /** Doanh thu phân theo phương thức thanh toán (all-time) */
    private List<PaymentMethodStatsResponse> revenueByPaymentMethod;
    /** Doanh thu 7 ngày gần nhất (theo từng ngày, để vẽ chart) */
    private List<RevenueTrendItem> revenueLast7Days;
}
