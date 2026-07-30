package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PricingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PricingPolicyRepository pricingPolicyRepository;

    @Cacheable(value = "pricingPolicies", key = "#vehicleTypeId", unless = "#result == null")
    public PricingPolicy getActivePolicy(String vehicleTypeId) {
        return pricingPolicyRepository.findActiveForVehicleType(vehicleTypeId).orElse(null);
    }

    public BigDecimal calculateFee(String vehicleTypeId, int totalHours) {
        if (totalHours <= 0) {
            return ZERO;
        }
        PricingPolicy policy = getActivePolicy(vehicleTypeId);
        return policy == null ? ZERO : calculateByPolicy(policy, totalHours);
    }

    public BigDecimal calculateByPolicy(PricingPolicy policy, int totalHours) {
        if (policy == null || totalHours <= 0) {
            return ZERO;
        }

        int maxHours = policy.getMaxHours() != null && policy.getMaxHours() > 0 ? policy.getMaxHours() : 24;
        int hours = Math.min(Math.max(totalHours, 1), maxHours);

        String pricingType = normalizePricingType(policy);
        return switch (pricingType) {
            case "TIERED" -> calculateTieredFee(policy, hours);
            case "DAILY" -> calculateDailyFee(policy, hours);
            case "OVERNIGHT" -> calculateOvernightFee(policy, hours);
            default -> calculateHourlyFee(policy, hours);
        };
    }

    /** Base price shown on driver/staff UI (tier1 for TIERED policies). */
    public BigDecimal resolveDisplayBasePrice(PricingPolicy policy) {
        if (policy == null) {
            return ZERO;
        }
        if (isTieredPolicy(policy) && isPositive(policy.getTier1Price())) {
            return policy.getTier1Price();
        }
        return nz(policy.getBasePrice());
    }

    /** Hourly rate shown on driver/staff UI. */
    public BigDecimal resolveDisplayHourlyRate(PricingPolicy policy) {
        if (policy == null) {
            return ZERO;
        }
        if (isTieredPolicy(policy)) {
            if (isPositive(policy.getTier1Price()) && isPositive(policy.getTier2Price())
                    && policy.getTier1Hours() != null && policy.getTier2Hours() != null
                    && policy.getTier2Hours() > policy.getTier1Hours()) {
                BigDecimal delta = policy.getTier2Price().subtract(policy.getTier1Price());
                int hourSpan = policy.getTier2Hours() - policy.getTier1Hours();
                return delta.divide(BigDecimal.valueOf(hourSpan), 0, RoundingMode.HALF_UP);
            }
            if (isPositive(policy.getTier2Price())) {
                return policy.getTier2Price();
            }
            return ZERO;
        }
        return nz(policy.getHourlyRate());
    }

    public Integer resolveDisplayMaxHours(PricingPolicy policy) {
        if (policy == null) {
            return null;
        }
        if (policy.getMaxHours() != null && policy.getMaxHours() > 0) {
            return policy.getMaxHours();
        }
        if (policy.getTier4Hours() != null && policy.getTier4Hours() > 0) {
            return policy.getTier4Hours();
        }
        return 24;
    }

    /**
     * Fee stored on session (incident adjustment or check-in estimate).
     * Returns null when session has no meaningful stored fee yet.
     */
    public BigDecimal resolveStoredSessionFee(fpt.swp391.parkingmanagement.entity.ParkingSession session) {
        if (session == null) {
            return null;
        }
        if (session.getTotalFee() != null && session.getTotalFee().compareTo(ZERO) > 0) {
            return session.getTotalFee();
        }
        if (session.getEstimatedFee() != null && session.getEstimatedFee().compareTo(ZERO) > 0) {
            return session.getEstimatedFee();
        }
        return null;
    }

    public String resolveVehicleTypeId(
            fpt.swp391.parkingmanagement.entity.Vehicle vehicle,
            fpt.swp391.parkingmanagement.entity.ParkingSlot slot) {
        if (vehicle != null && vehicle.getVehicleType() != null) {
            return vehicle.getVehicleType().getVehicleTypeId();
        }
        if (slot != null && slot.getZone() != null && slot.getZone().getFloor() != null
                && slot.getZone().getFloor().getVehicleType() != null) {
            return slot.getZone().getFloor().getVehicleType().getVehicleTypeId();
        }
        return null;
    }

    public String resolveVehicleTypeId(fpt.swp391.parkingmanagement.entity.Reservation reservation) {
        if (reservation == null) {
            return null;
        }
        return resolveVehicleTypeId(reservation.getVehicle(), reservation.getSlot());
    }

    public BigDecimal resolveReservationEstimatedFee(
            fpt.swp391.parkingmanagement.entity.Reservation reservation,
            fpt.swp391.parkingmanagement.entity.ParkingSession session,
            PricingPolicy policy) {
        BigDecimal storedSessionFee = resolveStoredSessionFee(session);
        if (storedSessionFee != null) {
            return storedSessionFee;
        }
        if (reservation != null && reservation.getEstimatedFee() != null
                && reservation.getEstimatedFee().compareTo(ZERO) > 0) {
            return reservation.getEstimatedFee();
        }
        if (policy != null) {
            return calculateByPolicy(policy, 1);
        }
        return ZERO;
    }

    public BigDecimal resolveGuestSessionEstimatedFee(
            fpt.swp391.parkingmanagement.entity.ParkingSession session,
            PricingPolicy policy,
            int parkingMinutes) {
        BigDecimal stored = resolveStoredSessionFee(session);
        if (stored != null) {
            return stored;
        }
        if (policy != null) {
            int hours = Math.max(1, (int) Math.ceil(Math.max(parkingMinutes, 1) / 60.0));
            return calculateByPolicy(policy, hours);
        }
        if (session != null && session.getEstimatedFee() != null
                && session.getEstimatedFee().compareTo(ZERO) > 0) {
            return session.getEstimatedFee();
        }
        return ZERO;
    }

    @CacheEvict(value = "pricingPolicies", allEntries = true)
    public void evictAllPricingCache() {
        // Evict cache when Manager updates pricing policy
    }

    private BigDecimal calculateHourlyFee(PricingPolicy policy, int hours) {
        BigDecimal basePrice = nz(policy.getBasePrice());
        BigDecimal hourlyRate = nz(policy.getHourlyRate());
        if (basePrice.compareTo(ZERO) == 0 && hourlyRate.compareTo(ZERO) == 0 && hasTierPricing(policy)) {
            return calculateTieredFee(policy, hours);
        }
        return basePrice.add(hourlyRate.multiply(BigDecimal.valueOf(Math.max(0, hours - 1))));
    }

    private BigDecimal calculateDailyFee(PricingPolicy policy, int hours) {
        if (isPositive(policy.getPerDayPrice())) {
            int days = Math.max(1, (int) Math.ceil(hours / 24.0));
            return policy.getPerDayPrice().multiply(BigDecimal.valueOf(days));
        }
        return calculateHourlyFee(policy, hours);
    }

    private BigDecimal calculateOvernightFee(PricingPolicy policy, int hours) {
        BigDecimal overnight = nz(policy.getOvernightFee());
        if (overnight.compareTo(ZERO) > 0) {
            return overnight.add(calculateHourlyFee(policy, Math.max(1, hours)));
        }
        return calculateHourlyFee(policy, hours);
    }

    private BigDecimal calculateTieredFee(PricingPolicy policy, int hours) {
        if (hours > 24 && isPositive(policy.getPerDayPrice())) {
            int fullDays = hours / 24;
            int remainder = hours % 24;
            BigDecimal fee = policy.getPerDayPrice().multiply(BigDecimal.valueOf(fullDays));
            if (remainder > 0) {
                fee = fee.add(calculateTieredFeeForSingleDay(policy, Math.max(1, remainder)));
            }
            return fee;
        }
        return calculateTieredFeeForSingleDay(policy, hours);
    }

    private BigDecimal calculateTieredFeeForSingleDay(PricingPolicy policy, int hours) {
        int h = Math.max(1, hours);
        if (policy.getTier1Hours() != null && policy.getTier1Hours() > 0 && h <= policy.getTier1Hours()
                && isPositive(policy.getTier1Price())) {
            return policy.getTier1Price();
        }
        if (policy.getTier2Hours() != null && policy.getTier2Hours() > 0 && h <= policy.getTier2Hours()
                && isPositive(policy.getTier2Price())) {
            return policy.getTier2Price();
        }
        if (policy.getTier3Hours() != null && policy.getTier3Hours() > 0 && h <= policy.getTier3Hours()
                && isPositive(policy.getTier3Price())) {
            return policy.getTier3Price();
        }
        if (policy.getTier4Hours() != null && policy.getTier4Hours() > 0 && h <= policy.getTier4Hours()
                && isPositive(policy.getTier4Price())) {
            return policy.getTier4Price();
        }
        if (isPositive(policy.getTier4Price())) {
            return policy.getTier4Price();
        }
        if (isPositive(policy.getPerDayPrice())) {
            return policy.getPerDayPrice();
        }
        return calculateHourlyFee(policy, h);
    }

    private boolean hasTierPricing(PricingPolicy policy) {
        return isPositive(policy.getTier1Price())
                || isPositive(policy.getTier2Price())
                || isPositive(policy.getTier3Price())
                || isPositive(policy.getTier4Price());
    }

    private boolean isTieredPolicy(PricingPolicy policy) {
        return "TIERED".equals(normalizePricingType(policy)) || hasTierPricing(policy);
    }

    private String normalizePricingType(PricingPolicy policy) {
        if (policy.getPricingType() == null || policy.getPricingType().isBlank()) {
            return hasTierPricing(policy) ? "TIERED" : "HOURLY";
        }
        return policy.getPricingType().trim().toUpperCase();
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(ZERO) > 0;
    }
}
