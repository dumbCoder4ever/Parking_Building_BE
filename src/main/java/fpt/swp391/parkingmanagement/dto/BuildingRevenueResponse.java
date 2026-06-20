package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingRevenueResponse {

    private String buildingId;
    private String buildingName;
    private BigDecimal totalRevenue;
    private long paymentCount;
}
