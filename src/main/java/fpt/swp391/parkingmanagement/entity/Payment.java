package fpt.swp391.parkingmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ParkingSession session;

    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    private BigDecimal amount;

    private LocalDateTime paymentTime;

    @Column(name = "payment_status", length = 30)
    private String paymentStatus;

    private String transactionCode;

    private String note;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (paymentId == null) paymentId = UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }
}
