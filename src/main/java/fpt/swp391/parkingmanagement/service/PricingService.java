package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final PricingPolicyRepository pricingPolicyRepository;

    @Cacheable(value = "pricingPolicies", key = "#vehicleTypeId", unless = "#result == null")
    public PricingPolicy getActivePolicy(String vehicleTypeId) {
        return pricingPolicyRepository.findActiveForVehicleType(vehicleTypeId).orElse(null);
    }

    public BigDecimal calculateFee(String vehicleTypeId, int totalHours) {
        if (totalHours <= 0) return BigDecimal.ZERO;
        PricingPolicy policy = getActivePolicy(vehicleTypeId);
        if (policy == null) {
            log.warn("[PRICING] No active policy for vehicleTypeId={}", vehicleTypeId);
            return BigDecimal.ZERO;
        }
        BigDecimal fee = calculateByPolicy(policy, totalHours);
        log.info("[PRICING] calculateFee vehicleTypeId={}, totalHours={}, policyId={}, pricingType={}, "
                + "basePrice={}, hourlyRate={}, maxHours={} "
                + "(tier1={}h/{}d, tier2={}h/{}d, tier3={}h/{}d, tier4={}h/{}d, perDay={}d) -> fee={}",
                vehicleTypeId, totalHours, policy.getPolicyId(), policy.getPricingType(),
                policy.getBasePrice(), policy.getHourlyRate(), policy.getMaxHours(),
                policy.getTier1Hours(), policy.getTier1Price(),
                policy.getTier2Hours(), policy.getTier2Price(),
                policy.getTier3Hours(), policy.getTier3Price(),
                policy.getTier4Hours(), policy.getTier4Price(),
                policy.getPerDayPrice(), fee);
        return fee;
    }

    public BigDecimal calculateByPolicy(PricingPolicy policy, int totalHours) {
        if (policy == null || totalHours <= 0) return BigDecimal.ZERO;

        int maxHours = policy.getMaxHours() != null ? policy.getMaxHours() : 24;
        int hours = Math.min(totalHours, maxHours);

        BigDecimal basePrice = nz(policy.getBasePrice());
        BigDecimal hourlyRate = nz(policy.getHourlyRate());

        String type = policy.getPricingType() == null ? "HOURLY" : policy.getPricingType().toUpperCase();

        // TIERED: giá theo bậc thang. Ví dụ Car: <=2h=20k, <=6h=40k, <=12h=60k, <=24h=100k, +1 ngày=100k
        if ("TIERED".equals(type)) {
            return calculateTiered(policy, hours);
        }

        // DAILY: charge theo ngày (perDayPrice * ceil(hours/24))
        if ("DAILY".equals(type)) {
            BigDecimal perDay = nz(policy.getPerDayPrice());
            if (perDay.compareTo(BigDecimal.ZERO) == 0) perDay = basePrice;
            int days = Math.max(1, (int) Math.ceil(hours / 24.0));
            return perDay.multiply(BigDecimal.valueOf(days));
        }

        // OVERNIGHT: charge overnight_fee cho mỗi đêm
        if ("OVERNIGHT".equals(type)) {
            BigDecimal overnight = nz(policy.getOvernightFee());
            if (overnight.compareTo(BigDecimal.ZERO) == 0) overnight = basePrice;
            int nights = Math.max(1, (int) Math.ceil(hours / 24.0));
            return overnight.multiply(BigDecimal.valueOf(nights));
        }

        // HOURLY (default): basePrice + hourlyRate * (hours - 1)
        return basePrice.add(hourlyRate.multiply(BigDecimal.valueOf(hours - 1)));
    }

    /**
     * Tính giá theo bậc thang:
     *   - Nếu totalHours <= tier1Hours -> tier1Price
     *   - Nếu totalHours <= tier2Hours -> tier2Price
     *   - ... đến tier4
     *   - Nếu vượt tier4 (>= 1 ngày) -> perDayPrice cho mỗi ngày
     * Trả về 0 nếu không có tier nào được set.
     */
    private BigDecimal calculateTiered(PricingPolicy policy, int hours) {
        if (hours <= 0) return BigDecimal.ZERO;

        Integer[] tierHours = {
            policy.getTier1Hours(),
            policy.getTier2Hours(),
            policy.getTier3Hours(),
            policy.getTier4Hours()
        };
        BigDecimal[] tierPrices = {
            nz(policy.getTier1Price()),
            nz(policy.getTier2Price()),
            nz(policy.getTier3Price()),
            nz(policy.getTier4Price())
        };

        // Duyệt TUẦN TỰ từ tier1 đến tier4 — return tier đầu tiên match
        // Fix: trước đây duyệt NGƯỢC từ tier4 → tier1, khiến parkingHours=11
        // bị tier4 (limit=24) match trước tier3 (limit=12) → trả 100000 sai.
        for (int i = 0; i < tierHours.length; i++) {
            int limit = tierHours[i] != null ? tierHours[i] : 0;
            if (limit > 0 && hours <= limit && tierPrices[i].compareTo(BigDecimal.ZERO) > 0) {
                return tierPrices[i];
            }
        }

        // Vượt tất cả tier (vd >=24h): tính theo perDayPrice cho mỗi ngày
        BigDecimal perDay = nz(policy.getPerDayPrice());
        if (perDay.compareTo(BigDecimal.ZERO) > 0) {
            int days = Math.max(1, (int) Math.ceil(hours / 24.0));
            return perDay.multiply(BigDecimal.valueOf(days));
        }

        // Fallback nếu tier rỗng: dùng basePrice + hourlyRate
        return nz(policy.getBasePrice())
                .add(nz(policy.getHourlyRate()).multiply(BigDecimal.valueOf(hours - 1)));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    @CacheEvict(value = "pricingPolicies", allEntries = true)
    public void evictAllPricingCache() {
        // Method rỗng - chỉ để evict cache khi Manager cập nhật pricing policy
    }
}
