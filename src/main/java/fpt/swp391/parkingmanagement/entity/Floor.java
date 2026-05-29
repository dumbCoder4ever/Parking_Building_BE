package fpt.swp391.parkingmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "floors")
public class Floor {

    @Id
    @Column(name = "floor_id", length = 36)
    private String floorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Building building;

    @Column(name = "floor_name")
    private String floorName;

    @Column(name = "floor_level")
    private Integer floorLevel;

    private Integer maxCapacity;

    private Integer currentOccupancy;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (floorId == null) floorId = UUID.randomUUID().toString();
        if (status == null) status = "ACTIVE";
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
