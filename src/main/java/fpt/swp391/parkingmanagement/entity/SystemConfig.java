package fpt.swp391.parkingmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "system_configs")
public class SystemConfig {

    @Id
    @Column(name = "config_id", length = 36)
    private String configId;

    @Column(name = "config_key", nullable = false, unique = true, length = 80)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 200)
    private String configValue;

    @Column(length = 500)
    private String description;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (configId == null) {
            configId = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
