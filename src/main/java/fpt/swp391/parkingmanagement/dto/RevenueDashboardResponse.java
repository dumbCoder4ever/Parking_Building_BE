package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueDashboardResponse {

    private BigDecimal totalRevenue;
    private long totalPaymentCount;
    private LocalDateTime from;
    private LocalDateTime to;
    private List<BuildingRevenueResponse> buildings;
}
