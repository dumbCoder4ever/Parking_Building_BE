package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTierTest {

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(null);
    }

    @Test
    void calculateByPolicy_tieredCar_firstBracket_returnsTier1Price() {
        PricingPolicy policy = carTieredPolicy();

        assertThat(pricingService.calculateByPolicy(policy, 1))
                .isEqualByComparingTo(new BigDecimal("20000"));
    }

    @Test
    void calculateByPolicy_tieredCar_secondBracket_returnsTier2Price() {
        PricingPolicy policy = carTieredPolicy();

        assertThat(pricingService.calculateByPolicy(policy, 4))
                .isEqualByComparingTo(new BigDecimal("40000"));
    }

    @Test
    void resolveDisplayBasePrice_tieredPolicy_usesTier1Price() {
        PricingPolicy policy = carTieredPolicy();

        assertThat(pricingService.resolveDisplayBasePrice(policy))
                .isEqualByComparingTo(new BigDecimal("20000"));
    }

    @Test
    void calculateByPolicy_hourlyPolicy_unchanged() {
        PricingPolicy policy = new PricingPolicy();
        policy.setPricingType("HOURLY");
        policy.setBasePrice(new BigDecimal("5000"));
        policy.setHourlyRate(new BigDecimal("3000"));
        policy.setMaxHours(24);

        assertThat(pricingService.calculateByPolicy(policy, 3))
                .isEqualByComparingTo(new BigDecimal("11000"));
    }

    private PricingPolicy carTieredPolicy() {
        PricingPolicy policy = new PricingPolicy();
        policy.setPricingType("TIERED");
        policy.setTier1Hours(2);
        policy.setTier1Price(new BigDecimal("20000"));
        policy.setTier2Hours(6);
        policy.setTier2Price(new BigDecimal("40000"));
        policy.setTier3Hours(12);
        policy.setTier3Price(new BigDecimal("60000"));
        policy.setTier4Hours(24);
        policy.setTier4Price(new BigDecimal("100000"));
        policy.setPerDayPrice(new BigDecimal("100000"));
        policy.setMaxHours(24);
        return policy;
    }
}
