package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    boolean existsByVehicleVehicleIdAndReservationStatusIn(String vehicleId, Collection<String> statuses);

    Optional<Reservation> findByReservationCode(String reservationCode);

    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    Optional<Reservation> findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc(
            String slotId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    List<Reservation> findByUserUserIdOrderByCreatedAtDesc(String userId);

    @Query("SELECT r FROM Reservation r WHERE r.user.userId = :userId " +
           "AND r.reservationStatus IN :statuses " +
           "AND r.reservationStart < :endTime AND r.reservationEnd > :startTime")
    List<Reservation> findOverlappingReservations(
            @Param("userId") String userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statuses") Collection<String> statuses);
}
