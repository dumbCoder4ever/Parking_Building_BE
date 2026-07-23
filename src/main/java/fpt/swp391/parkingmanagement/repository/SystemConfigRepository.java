package fpt.swp391.parkingmanagement.repository;

import fpt.swp391.parkingmanagement.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {

    Optional<SystemConfig> findByConfigKeyIgnoreCase(String configKey);

    boolean existsByConfigKeyIgnoreCase(String configKey);
}
