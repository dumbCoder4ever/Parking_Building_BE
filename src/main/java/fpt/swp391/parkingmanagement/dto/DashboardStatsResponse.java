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
    private LocalDateTime generatedAt;
    private OccupancyStatsResponse occupancy;
    private SessionStatsResponse sessions;
    private ReservationStatsResponse reservations;
    private UserStatsResponse users;
    private IncidentStatsResponse incidents;
    private List<PaymentMethodStatsResponse> revenueByPaymentMethod;
    private List<RevenueTrendItem> revenueTrend;
    private List<RevenueTrendByMethodItem> revenueTrendByPaymentMethod;
}
