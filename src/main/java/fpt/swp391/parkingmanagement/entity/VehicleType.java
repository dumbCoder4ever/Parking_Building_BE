package fpt.swp391.parkingmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "vehicle_types")
public class VehicleType {

    @Id
    @Column(name = "vehicle_type_id", length = 36)
    private String vehicleTypeId;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    private String sizeCategory;

    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (vehicleTypeId == null) vehicleTypeId = UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }
}
