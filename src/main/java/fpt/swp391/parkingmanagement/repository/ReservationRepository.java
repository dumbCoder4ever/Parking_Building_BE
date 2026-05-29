package fpt.swp391.parkingmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
}
