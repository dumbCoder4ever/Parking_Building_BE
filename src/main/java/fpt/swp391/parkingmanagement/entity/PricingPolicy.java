package fpt.swp391.parkingmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "pricing_policies")
public class PricingPolicy {

    @Id
    @Column(name = "policy_id", length = 36)
    private String policyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private VehicleType vehicleType;

    @Column(name = "policy_name")
    private String policyName;

    @Column(name = "pricing_type", length = 40)
    private String pricingType;

    private BigDecimal basePrice;

    private BigDecimal hourlyRate;

    private BigDecimal overnightFee;

    // Tiered pricing — limits (in hours), 0 means unused
    @Column(name = "tier1_hours")
    private Integer tier1Hours;

    @Column(name = "tier1_price")
    private BigDecimal tier1Price;

    @Column(name = "tier2_hours")
    private Integer tier2Hours;

    @Column(name = "tier2_price")
    private BigDecimal tier2Price;

    @Column(name = "tier3_hours")
    private Integer tier3Hours;

    @Column(name = "tier3_price")
    private BigDecimal tier3Price;

    @Column(name = "tier4_hours")
    private Integer tier4Hours;

    @Column(name = "tier4_price")
    private BigDecimal tier4Price;

    @Column(name = "per_day_price")
    private BigDecimal perDayPrice;

    private BigDecimal lostTicketFee;

    private BigDecimal peakHourMultiplier;

    private BigDecimal maxDailyFee;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveTo;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (policyId == null) policyId = UUID.randomUUID().toString();
        if (status == null) status = "ACTIVE";
        createdAt = LocalDateTime.now();
    }
}
