package fpt.swp391.parkingmanagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {
    Optional<Vehicle> findByPlateNumber(String plateNumber);
}
