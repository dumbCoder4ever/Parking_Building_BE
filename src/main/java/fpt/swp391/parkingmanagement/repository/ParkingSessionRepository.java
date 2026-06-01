package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.ParkingSession;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, String> {
    List<ParkingSession> findByVehicleVehicleId(String vehicleId);

    boolean existsByVehicleVehicleIdAndSessionStatus(String vehicleId, String sessionStatus);
}
