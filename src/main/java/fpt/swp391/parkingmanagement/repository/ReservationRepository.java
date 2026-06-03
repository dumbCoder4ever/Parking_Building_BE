package fpt.swp391.parkingmanagement.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    boolean existsByVehicleVehicleIdAndReservationStatusIn(String vehicleId, Collection<String> statuses);
}
