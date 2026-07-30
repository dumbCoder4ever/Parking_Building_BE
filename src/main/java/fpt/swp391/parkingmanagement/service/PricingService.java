package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingPolicyRepository pricingPolicyRepository;

    @Cacheable(value = "pricingPolicies", key = "#vehicleTypeId", unless = "#result == null")
    public PricingPolicy getActivePolicy(String vehicleTypeId) {
        return pricingPolicyRepository.findActiveForVehicleType(vehicleTypeId).orElse(null);
    }

    public BigDecimal calculateFee(String vehicleTypeId, int totalHours) {
        if (totalHours <= 0) return BigDecimal.ZERO;
        PricingPolicy policy = getActivePolicy(vehicleTypeId);
        return policy == null ? BigDecimal.ZERO : calculateByPolicy(policy, totalHours);
    }

    public BigDecimal calculateByPolicy(PricingPolicy policy, int totalHours) {
        if (policy == null || totalHours <= 0) return BigDecimal.ZERO;

        int maxHours = policy.getMaxHours() != null ? policy.getMaxHours() : 24;
        int hours = Math.min(totalHours, maxHours);

        BigDecimal basePrice = policy.getBasePrice() != null ? policy.getBasePrice() : BigDecimal.ZERO;
        BigDecimal hourlyRate = policy.getHourlyRate() != null ? policy.getHourlyRate() : BigDecimal.ZERO;

        return basePrice.add(hourlyRate.multiply(BigDecimal.valueOf(hours - 1)));
    }

    /**
     * Fee stored on session (incident adjustment or check-in estimate).
     * Returns null when session has no meaningful stored fee yet.
     */
    public BigDecimal resolveStoredSessionFee(fpt.swp391.parkingmanagement.entity.ParkingSession session) {
        if (session == null) {
            return null;
        }
        if (session.getTotalFee() != null && session.getTotalFee().compareTo(BigDecimal.ZERO) > 0) {
            return session.getTotalFee();
        }
        if (session.getEstimatedFee() != null && session.getEstimatedFee().compareTo(BigDecimal.ZERO) > 0) {
            return session.getEstimatedFee();
        }
        return null;
    }

    /**
     * Resolve vehicle type from vehicle entity or slot floor (fallback when vehicle type missing).
     */
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
                && reservation.getEstimatedFee().compareTo(BigDecimal.ZERO) > 0) {
            return reservation.getEstimatedFee();
        }
        if (policy != null) {
            return calculateByPolicy(policy, 1);
        }
        return BigDecimal.ZERO;
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
                && session.getEstimatedFee().compareTo(BigDecimal.ZERO) > 0) {
            return session.getEstimatedFee();
        }
        return BigDecimal.ZERO;
    }

    @CacheEvict(value = "pricingPolicies", allEntries = true)
    public void evictAllPricingCache() {
        // Method rỗng - chỉ để evict cache khi Manager cập nhật pricing policy
    }
}
