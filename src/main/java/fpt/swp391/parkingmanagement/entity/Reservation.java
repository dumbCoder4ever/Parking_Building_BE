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
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(name = "reservation_id", length = 36)
    private String reservationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ParkingSlot slot;

    @Column(name = "reservation_code")
    private String reservationCode;

    private LocalDateTime reservationStart;

    // reservationEnd removed - driver can checkout anytime, payment calculated by actual parking time

    @Column(name = "grace_period_minutes")
    private Integer gracePeriodMinutes = 15;

    private BigDecimal estimatedFee;

    @Column(name = "reservation_status", length = 30)
    private String reservationStatus;

    private String note;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (reservationId == null) reservationId = UUID.randomUUID().toString();
        if (gracePeriodMinutes == null) gracePeriodMinutes = 15;
        if (reservationStatus == null) reservationStatus = "PENDING";
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
