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

        BigDecimal total = BigDecimal.ZERO;
        int remaining = totalHours;

        if (t1 > 0 && remaining > 0) {
            int used = Math.min(remaining, t1);
            total = total.add(p1.multiply(BigDecimal.valueOf(used)));
            remaining -= used;
        }
        if (t2 > 0 && remaining > 0) {
            int used = Math.min(remaining, t2 - t1);
            total = total.add(p2.multiply(BigDecimal.valueOf(used)));
            remaining -= used;
        }
        if (t3 > 0 && remaining > 0) {
            int used = Math.min(remaining, t3 - t2);
            total = total.add(p3.multiply(BigDecimal.valueOf(used)));
            remaining -= used;
        }
        if (t4 > 0 && remaining > 0) {
            int used = Math.min(remaining, t4 - t3);
            total = total.add(p4.multiply(BigDecimal.valueOf(used)));
            remaining -= used;
        }
        if (remaining > 0 && perDay.compareTo(BigDecimal.ZERO) > 0) {
            int days = (int) Math.ceil((double) remaining / 24.0);
            total = total.add(perDay.multiply(BigDecimal.valueOf(days)));
        }

        return total;
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
            tiers.add(PricingTierResponse.builder()
                    .tierLabel(PricingTierResponse.TIER_DAY_SUFFIX)
                    .maxHours(Integer.MAX_VALUE)
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
        for (int i = 0; i < tiers.size(); i++) {
            PricingTierResponse tier = tiers.get(i);
            if (totalHours <= tier.getMaxHours() || i == tiers.size() - 1) {
                sb.append(tier.getTierLabel()).append(" = ").append(tier.getPrice()).append(" VND");
                break;
            }
        }
        return sb.toString();
    }
}
