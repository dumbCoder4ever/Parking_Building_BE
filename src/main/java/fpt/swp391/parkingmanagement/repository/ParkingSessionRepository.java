package fpt.swp391.parkingmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.ParkingSession;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, String> {
    List<ParkingSession> findByVehicleVehicleId(String vehicleId);
    Optional<ParkingSession> findByTicketTicketIdAndSessionStatus(String ticketId, String sessionStatus);

    boolean existsByVehicleVehicleIdAndSessionStatus(String vehicleId, String sessionStatus);
}
