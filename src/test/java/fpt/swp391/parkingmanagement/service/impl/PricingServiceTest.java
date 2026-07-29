package fpt.swp391.parkingmanagement.service.impl;

import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import fpt.swp391.parkingmanagement.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingPolicyRepository pricingPolicyRepository;

    @InjectMocks
    private PricingService pricingService;

    private PricingPolicy motorbikePolicy;
    private PricingPolicy carPolicy;
    private PricingPolicy suvPolicy;
    private PricingPolicy truckPolicy;

    private static final String MOTORBIKE_ID = "33333333-3333-3333-3333-333333333331";
    private static final String CAR_ID = "33333333-3333-3333-3333-333333333332";
    private static final String SUV_ID = "33333333-3333-3333-3333-333333333333";
    private static final String TRUCK_ID = "33333333-3333-3333-3333-333333333334";

    @BeforeEach
    void setUp() {
        motorbikePolicy = createPolicy(MOTORBIKE_ID, "TIERED_HOURLY", 3000, 3000, 3);
        carPolicy = createPolicy(CAR_ID, "TIERED_HOURLY", 5000, 5000, 3);
        suvPolicy = createPolicy(SUV_ID, "TIERED_HOURLY", 7000, 7000, 3);
        truckPolicy = createPolicy(TRUCK_ID, "TIERED_HOURLY", 10000, 10000, 3);
    }

    private PricingPolicy createPolicy(String vehicleTypeId, String pricingType,
                                      int basePrice, int hourlyRate, Integer includedHours) {
        PricingPolicy policy = new PricingPolicy();
        policy.setPolicyId(UUID.randomUUID().toString());
        policy.setPricingType(pricingType);
        policy.setBasePrice(BigDecimal.valueOf(basePrice));
        policy.setHourlyRate(BigDecimal.valueOf(hourlyRate));
        policy.setIncludedHours(includedHours);
        policy.setStatus("ACTIVE");

        VehicleType vehicleType = new VehicleType();
        vehicleType.setVehicleTypeId(vehicleTypeId);
        policy.setVehicleType(vehicleType);

        return policy;
    }

    @Nested
    @DisplayName("TIERED_HOURLY - Motorbike (3k/3h, then 3k/h)")
    class MotorbikeTieredHourly {

        @BeforeEach
        void setUp() {
            when(pricingPolicyRepository.findActiveForVehicleType(MOTORBIKE_ID))
                    .thenReturn(Optional.of(motorbikePolicy));
        }

        @Test
        @DisplayName("1 hour -> 3000 VND (within included hours)")
        void oneHour_ReturnsBasePrice() {
            BigDecimal fee = pricingService.calculateFee(MOTORBIKE_ID, 1);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        @DisplayName("3 hours -> 3000 VND (at included hours boundary)")
        void threeHours_ReturnsBasePrice() {
            BigDecimal fee = pricingService.calculateFee(MOTORBIKE_ID, 3);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        @DisplayName("4 hours -> 6000 VND (1 extra hour)")
        void fourHours_ReturnsBasePlusOneHour() {
            BigDecimal fee = pricingService.calculateFee(MOTORBIKE_ID, 4);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(6000));
        }

        @Test
        @DisplayName("6 hours -> 6000 VND (2 blocks of 3h)")
        void sixHours_ReturnsCorrectFee() {
            BigDecimal fee = pricingService.calculateFee(MOTORBIKE_ID, 6);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(6000));
        }

        @Test
        @DisplayName("12 hours -> 12000 VND (4 blocks of 3h)")
        void twelveHours_ReturnsCorrectFee() {
            BigDecimal fee = pricingService.calculateFee(MOTORBIKE_ID, 12);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(12000));
        }

        @Test
        @DisplayName("24 hours -> 24000 VND (8 blocks of 3h)")
        void twentyFourHours_ReturnsCorrectFee() {
            BigDecimal fee = pricingService.calculateFee(MOTORBIKE_ID, 24);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(24000));
        }
    }

    @Nested
    @DisplayName("TIERED_HOURLY - Car (5k/3h, then 5k/h)")
    class CarTieredHourly {

        @BeforeEach
        void setUp() {
            when(pricingPolicyRepository.findActiveForVehicleType(CAR_ID))
                    .thenReturn(Optional.of(carPolicy));
        }

        @Test
        @DisplayName("2 hours -> 5000 VND (within included hours)")
        void twoHours_ReturnsBasePrice() {
            BigDecimal fee = pricingService.calculateFee(CAR_ID, 2);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        @DisplayName("3 hours -> 5000 VND (at boundary)")
        void threeHours_ReturnsBasePrice() {
            BigDecimal fee = pricingService.calculateFee(CAR_ID, 3);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        @DisplayName("5 hours -> 10000 VND (2 blocks of 3h)")
        void fiveHours_ReturnsCorrectFee() {
            BigDecimal fee = pricingService.calculateFee(CAR_ID, 5);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }

        @Test
        @DisplayName("10 hours -> 20000 VND (4 blocks of 3h)")
        void tenHours_ReturnsCorrectFee() {
            BigDecimal fee = pricingService.calculateFee(CAR_ID, 10);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(20000));
        }
    }

    @Nested
    @DisplayName("TIERED_HOURLY - SUV (7k/3h, then 7k/h)")
    class SuvTieredHourly {

        @BeforeEach
        void setUp() {
            when(pricingPolicyRepository.findActiveForVehicleType(SUV_ID))
                    .thenReturn(Optional.of(suvPolicy));
        }

        @Test
        @DisplayName("1 hour -> 7000 VND (within included hours)")
        void oneHour_ReturnsBasePrice() {
            BigDecimal fee = pricingService.calculateFee(SUV_ID, 1);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(7000));
        }

        @Test
        @DisplayName("3 hours -> 7000 VND (at boundary)")
        void threeHours_ReturnsBasePrice() {
            BigDecimal fee = pricingService.calculateFee(SUV_ID, 3);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(7000));
        }

        @Test
        @DisplayName("5 hours -> 14000 VND (2 blocks of 3h)")
        void fiveHours_ReturnsCorrectFee() {
            BigDecimal fee = pricingService.calculateFee(SUV_ID, 5);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(14000));
        }
    }

    @Nested
    @DisplayName("TIERED_HOURLY - Truck (10k/3h, then 10k/h)")
    class TruckTieredHourly {

        @BeforeEach
        void setUp() {
            when(pricingPolicyRepository.findActiveForVehicleType(TRUCK_ID))
                    .thenReturn(Optional.of(truckPolicy));
        }

        @Test
        @DisplayName("1 hour -> 10000 VND (within included hours)")
        void oneHour_ReturnsBasePrice() {
            BigDecimal fee = pricingService.calculateFee(TRUCK_ID, 1);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }

        @Test
        @DisplayName("6 hours -> 20000 VND (2 blocks of 3h)")
        void sixHours_ReturnsCorrectFee() {
            BigDecimal fee = pricingService.calculateFee(TRUCK_ID, 6);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(20000));
        }

        @Test
        @DisplayName("10 hours -> 40000 VND (4 blocks of 3h)")
        void tenHours_ReturnsCorrectFee() {
            BigDecimal fee = pricingService.calculateFee(TRUCK_ID, 10);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(40000));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("0 hours -> 0 VND")
        void zeroHours_ReturnsZero() {
            BigDecimal fee = pricingService.calculateFee(MOTORBIKE_ID, 0);
            assertThat(fee).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Negative hours -> 0 VND")
        void negativeHours_ReturnsZero() {
            BigDecimal fee = pricingService.calculateFee(MOTORBIKE_ID, -5);
            assertThat(fee).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("No policy found -> 0 VND")
        void noPolicy_ReturnsZero() {
            when(pricingPolicyRepository.findActiveForVehicleType("unknown-id"))
                    .thenReturn(Optional.empty());
            BigDecimal fee = pricingService.calculateFee("unknown-id", 5);
            assertThat(fee).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("includedHours = null defaults to 3")
        void nullIncludedHours_DefaultsToThree() {
            PricingPolicy policyWithNullIncluded = createPolicy(CAR_ID, "TIERED_HOURLY", 5000, 5000, null);
            when(pricingPolicyRepository.findActiveForVehicleType(CAR_ID))
                    .thenReturn(Optional.of(policyWithNullIncluded));

            BigDecimal fee = pricingService.calculateFee(CAR_ID, 4);
            assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }
    }
}
