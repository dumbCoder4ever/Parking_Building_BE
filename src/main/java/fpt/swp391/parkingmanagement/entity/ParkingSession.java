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
@Table(name = "parking_sessions")
public class ParkingSession {

    public enum CheckinType {
        RESERVATION,
        DRIVER_WALK_IN,
        GUEST
    }

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkin_type", length = 30)
    private CheckinType checkinType;

    private LocalDateTime checkinTime;

    private LocalDateTime checkoutTime;

    private BigDecimal estimatedFee;

    private BigDecimal totalFee;

    /** Tổng thời gian đỗ (phút). */
    private Integer parkingDuration;

    @Column(name = "payment_status", length = 30)
    private String paymentStatus;

    @Column(name = "session_status", length = 30)
    private String sessionStatus;

    private String note;

    @Column(name = "guest_name", length = 100)
    private String guestName;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @Column(name = "checkin_vehicle_image")
    private String checkinVehicleImage;

    @Column(name = "checkout_vehicle_image")
    private String checkoutVehicleImage;

    @Column(name = "incident_authorized")
    private Boolean incidentAuthorized;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User updatedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (sessionId == null) sessionId = UUID.randomUUID().toString();
        if (sessionStatus == null) sessionStatus = "ACTIVE";
        if (paymentStatus == null) paymentStatus = "UNPAID";
        if (estimatedFee == null) estimatedFee = BigDecimal.ZERO;
        if (totalFee == null) totalFee = BigDecimal.ZERO;
        if (parkingDuration == null) parkingDuration = 0;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
