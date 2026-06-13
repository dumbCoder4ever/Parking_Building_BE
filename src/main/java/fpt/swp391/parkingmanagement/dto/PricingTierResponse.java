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
public class PricingTierResponse {
    private String tierLabel;
    private Integer maxHours;
    private BigDecimal price;

    public static final String TIER_DAY_SUFFIX = "+/day";
}
