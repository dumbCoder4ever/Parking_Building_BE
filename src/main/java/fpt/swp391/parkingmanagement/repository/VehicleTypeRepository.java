package fpt.swp391.parkingmanagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.VehicleType;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, String> {
    Optional<VehicleType> findByTypeName(String typeName);

    Optional<VehicleType> findByTypeNameIgnoreCase(String typeName);
}
