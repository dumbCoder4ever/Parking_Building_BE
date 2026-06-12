package fpt.swp391.parkingmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {

    Optional<Vehicle> findByPlateNumberIgnoreCase(String plateNumber);

    Optional<Vehicle> findByVehicleIdAndUserUserId(String vehicleId, String userId);

    List<Vehicle> findByUserUserIdOrderByCreatedAtDesc(String userId);

    @EntityGraph(attributePaths = {"user", "vehicleType"})
    List<Vehicle> findByPlateNumberContainingIgnoreCaseOrderByCreatedAtDesc(String plateNumber);

    @EntityGraph(attributePaths = {"user", "vehicleType"})
    List<Vehicle> findAllByOrderByCreatedAtDesc();

    boolean existsByPlateNumberIgnoreCase(String plateNumber);

    boolean existsByPlateNumberIgnoreCaseAndVehicleIdNot(String plateNumber, String vehicleId);

    boolean existsByVehicleTypeVehicleTypeId(String vehicleTypeId);

    List<Vehicle> findByUserUserId(String userId);

    boolean existsByPlateNumber(String plateNumber);

    int countByUserUserId(String userId);
}
