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

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "hourly_rate")
    private BigDecimal hourlyRate;

    @Column(name = "max_hours")
    private Integer maxHours;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (policyId == null) policyId = UUID.randomUUID().toString();
        if (status == null) status = "ACTIVE";
        if (maxHours == null) maxHours = 24;
        createdAt = LocalDateTime.now();
    }
}
