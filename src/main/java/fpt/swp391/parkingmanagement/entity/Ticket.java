package fpt.swp391.parkingmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "tickets")
public class Ticket {

    @Id
    @Column(name = "ticket_id", length = 36)
    private String ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "ticket_code")
    private String ticketCode;

    @Column(name = "qr_code")
    private String qrCode;

    private Boolean isUsed = false;

    private Boolean isLost = false;

    @Column(length = 30)
    private String status;

    private LocalDateTime issuedAt;

    private LocalDateTime expiredAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (ticketId == null) ticketId = UUID.randomUUID().toString();
        if (status == null) status = "ACTIVE";
        createdAt = LocalDateTime.now();
    }
}
