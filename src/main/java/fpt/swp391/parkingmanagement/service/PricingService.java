package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingPolicyRepository pricingPolicyRepository;

    public PricingPolicy getActivePolicy(String vehicleTypeId) {
        return pricingPolicyRepository.findActiveForVehicleType(vehicleTypeId).orElse(null);
    }

    public BigDecimal calculateFee(String vehicleTypeId, int totalHours) {
        if (totalHours <= 0) return BigDecimal.ZERO;
        return pricingPolicyRepository.findActiveForVehicleType(vehicleTypeId)
                .map(policy -> calculateByPolicy(policy, totalHours))
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal calculateByPolicy(PricingPolicy policy, int totalHours) {
        if (policy == null || totalHours <= 0) return BigDecimal.ZERO;
        
        int maxHours = policy.getMaxHours() != null ? policy.getMaxHours() : 24;
        int hours = Math.min(totalHours, maxHours);

        BigDecimal basePrice = policy.getBasePrice() != null ? policy.getBasePrice() : BigDecimal.ZERO;
        BigDecimal hourlyRate = policy.getHourlyRate() != null ? policy.getHourlyRate() : BigDecimal.ZERO;

        return basePrice.add(hourlyRate.multiply(BigDecimal.valueOf(hours - 1)));
    }
}
