package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.PricingTierResponse;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingPolicyRepository pricingPolicyRepository;

    public List<PricingTierResponse> getTiers(String vehicleTypeId) {
        return pricingPolicyRepository.findActiveForVehicleType(vehicleTypeId)
                .map(this::toTierList)
                .orElse(List.of());
    }

    public BigDecimal calculateFee(String vehicleTypeId, int totalHours) {
        if (totalHours <= 0) return BigDecimal.ZERO;
        return pricingPolicyRepository.findActiveForVehicleType(vehicleTypeId)
                .map(policy -> computeTieredFee(policy, totalHours))
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal calculateFeeByPolicy(PricingPolicy policy, int totalHours) {
        if (policy == null || totalHours <= 0) return BigDecimal.ZERO;
        return computeTieredFee(policy, totalHours);
    }

    public PricingPolicy getActivePolicy(String vehicleTypeId) {
        return pricingPolicyRepository.findActiveForVehicleType(vehicleTypeId).orElse(null);
    }

    private BigDecimal computeTieredFee(PricingPolicy policy, int totalHours) {
        if (totalHours <= 0) return BigDecimal.ZERO;

        int t1 = policy.getTier1Hours() != null ? policy.getTier1Hours() : 0;
        int t2 = policy.getTier2Hours() != null ? policy.getTier2Hours() : 0;
        int t3 = policy.getTier3Hours() != null ? policy.getTier3Hours() : 0;
        int t4 = policy.getTier4Hours() != null ? policy.getTier4Hours() : 0;
        BigDecimal p1 = policy.getTier1Price() != null ? policy.getTier1Price() : BigDecimal.ZERO;
        BigDecimal p2 = policy.getTier2Price() != null ? policy.getTier2Price() : BigDecimal.ZERO;
        BigDecimal p3 = policy.getTier3Price() != null ? policy.getTier3Price() : BigDecimal.ZERO;
        BigDecimal p4 = policy.getTier4Price() != null ? policy.getTier4Price() : BigDecimal.ZERO;
        BigDecimal perDay = policy.getPerDayPrice() != null ? policy.getPerDayPrice() : BigDecimal.ZERO;

        if (t1 == 0 && t2 == 0 && t3 == 0 && t4 == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseFee = BigDecimal.ZERO;
        int maxTierHours = 0;

        if (totalHours <= t1 && t1 > 0) {
            baseFee = p1;
            maxTierHours = t1;
        } else if (totalHours <= t2 && t2 > 0) {
            baseFee = p2;
            maxTierHours = t2;
        } else if (totalHours <= t3 && t3 > 0) {
            baseFee = p3;
            maxTierHours = t3;
        } else if (totalHours <= t4 && t4 > 0) {
            baseFee = p4;
            maxTierHours = t4;
        } else {
            baseFee = p4;
            maxTierHours = t4 > 0 ? t4 : 0;
        }

        if (maxTierHours <= 0) {
            return BigDecimal.ZERO;
        }

        int remainingHours = totalHours - maxTierHours;
        if (remainingHours > 0 && perDay.compareTo(BigDecimal.ZERO) > 0) {
            int extraDays = (int) Math.ceil((double) remainingHours / 24.0);
            baseFee = baseFee.add(perDay.multiply(BigDecimal.valueOf(extraDays)));
        }

        return baseFee;
    }

    public List<PricingTierResponse> toTierList(PricingPolicy policy) {
        List<PricingTierResponse> tiers = new ArrayList<>();
        String typeName = policy.getVehicleType() != null ? policy.getVehicleType().getTypeName() : "";

        if (policy.getTier1Hours() != null && policy.getTier1Hours() > 0
                && policy.getTier1Price() != null) {
            tiers.add(PricingTierResponse.builder()
                    .tierLabel("≤ " + policy.getTier1Hours() + "h")
                    .maxHours(policy.getTier1Hours())
                    .price(policy.getTier1Price())
                    .build());
        }
        if (policy.getTier2Hours() != null && policy.getTier2Hours() > 0
                && policy.getTier2Price() != null) {
            tiers.add(PricingTierResponse.builder()
                    .tierLabel("≤ " + policy.getTier2Hours() + "h")
                    .maxHours(policy.getTier2Hours())
                    .price(policy.getTier2Price())
                    .build());
        }
        if (policy.getTier3Hours() != null && policy.getTier3Hours() > 0
                && policy.getTier3Price() != null) {
            tiers.add(PricingTierResponse.builder()
                    .tierLabel("≤ " + policy.getTier3Hours() + "h")
                    .maxHours(policy.getTier3Hours())
                    .price(policy.getTier3Price())
                    .build());
        }
        if (policy.getTier4Hours() != null && policy.getTier4Hours() > 0
                && policy.getTier4Price() != null) {
            tiers.add(PricingTierResponse.builder()
                    .tierLabel("≤ " + policy.getTier4Hours() + "h")
                    .maxHours(policy.getTier4Hours())
                    .price(policy.getTier4Price())
                    .build());
        }
        if (policy.getPerDayPrice() != null && policy.getPerDayPrice().compareTo(BigDecimal.ZERO) > 0) {
            int dayMaxHours = policy.getTier4Hours() != null ? policy.getTier4Hours() : 24;
            tiers.add(PricingTierResponse.builder()
                    .tierLabel(PricingTierResponse.TIER_DAY_SUFFIX)
                    .maxHours(dayMaxHours)
                    .price(policy.getPerDayPrice())
                    .build());
        }

        return tiers;
    }

    public String buildFeeExplanation(PricingPolicy policy, int totalHours) {
        if (policy == null || totalHours <= 0) return "";
        List<PricingTierResponse> tiers = toTierList(policy);
        if (tiers.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(totalHours).append("h parking: ");

        BigDecimal perDay = policy.getPerDayPrice() != null ? policy.getPerDayPrice() : BigDecimal.ZERO;
        int t1 = policy.getTier1Hours() != null ? policy.getTier1Hours() : 0;
        int t2 = policy.getTier2Hours() != null ? policy.getTier2Hours() : 0;
        int t3 = policy.getTier3Hours() != null ? policy.getTier3Hours() : 0;
        int t4 = policy.getTier4Hours() != null ? policy.getTier4Hours() : 0;
        BigDecimal p1 = policy.getTier1Price() != null ? policy.getTier1Price() : BigDecimal.ZERO;
        BigDecimal p2 = policy.getTier2Price() != null ? policy.getTier2Price() : BigDecimal.ZERO;
        BigDecimal p3 = policy.getTier3Price() != null ? policy.getTier3Price() : BigDecimal.ZERO;
        BigDecimal p4 = policy.getTier4Price() != null ? policy.getTier4Price() : BigDecimal.ZERO;

        BigDecimal baseFee = BigDecimal.ZERO;
        int maxTierHours = 0;

        if (totalHours <= t1 && t1 > 0) {
            baseFee = p1;
            maxTierHours = t1;
            sb.append("≤ ").append(t1).append("h = ").append(p1).append(" VND");
        } else if (totalHours <= t2 && t2 > 0) {
            baseFee = p2;
            maxTierHours = t2;
            sb.append("≤ ").append(t2).append("h = ").append(p2).append(" VND");
        } else if (totalHours <= t3 && t3 > 0) {
            baseFee = p3;
            maxTierHours = t3;
            sb.append("≤ ").append(t3).append("h = ").append(p3).append(" VND");
        } else if (totalHours <= t4 && t4 > 0) {
            baseFee = p4;
            maxTierHours = t4;
            sb.append("≤ ").append(t4).append("h = ").append(p4).append(" VND");
        } else {
            baseFee = p4;
            maxTierHours = t4;
            int remaining = totalHours - maxTierHours;
            if (remaining > 0 && perDay.compareTo(BigDecimal.ZERO) > 0) {
                int extraDays = (int) Math.ceil((double) remaining / 24.0);
                BigDecimal extraFee = perDay.multiply(BigDecimal.valueOf(extraDays));
                sb.append("≤ ").append(t4).append("h = ").append(p4)
                  .append(" + ").append(extraDays).append("d × ")
                  .append(perDay).append(" = ").append(baseFee.add(extraFee)).append(" VND");
            } else {
                sb.append("≤ ").append(t4).append("h = ").append(p4).append(" VND");
            }
        }

        return sb.toString();
    }
}
