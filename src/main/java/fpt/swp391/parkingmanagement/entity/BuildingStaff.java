package fpt.swp391.parkingmanagement.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@NoArgsConstructor
@Table(
        name = "building_staff",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_building_staff",
                columnNames = {"building_id", "user_id"}))
public class BuildingStaff {

    @Id
    @Column(name = "assignment_id", length = 36)
    private String assignmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(name = "assigned_at", updatable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    public void prePersist() {
        if (assignmentId == null) {
            assignmentId = UUID.randomUUID().toString();
        }
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }
}
