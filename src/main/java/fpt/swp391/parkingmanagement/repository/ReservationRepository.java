package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fpt.swp391.parkingmanagement.entity.Reservation;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    boolean existsByVehicleVehicleIdAndReservationStatusIn(String vehicleId, Collection<String> statuses);

    Optional<Reservation> findByReservationCode(String reservationCode);

    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    Optional<Reservation> findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc(
            String slotId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"user"})
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

    @Query(value = "SELECT r.* FROM reservations r " +
           "JOIN parking_slots ps ON r.slot_id = ps.slot_id " +
           "WHERE r.reservation_status IN ('PENDING', 'APPROVED') " +
           "AND ps.slot_status = 'RESERVED' " +
           "AND DATE_ADD(r.reservation_end, INTERVAL r.grace_period_minutes MINUTE) <= :currentTime",
           nativeQuery = true)
    List<Reservation> findExpiredReservations(@Param("currentTime") LocalDateTime currentTime);

    @Query(value = "SELECT r.* FROM reservations r " +
           "WHERE r.reservation_status = 'PENDING' " +
           "AND DATE_ADD(r.reservation_end, INTERVAL r.grace_period_minutes MINUTE) <= :currentTime",
           nativeQuery = true)
    List<Reservation> findExpiredPendingReservations(@Param("currentTime") LocalDateTime currentTime);

    @Query(value = "SELECT r.* FROM reservations r " +
           "WHERE r.reservation_status = 'APPROVED' " +
           "AND DATE_ADD(r.reservation_end, INTERVAL r.grace_period_minutes MINUTE) <= :currentTime",
           nativeQuery = true)
    List<Reservation> findExpiredApprovedReservations(@Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT r FROM Reservation r WHERE r.user.userId = :userId " +
           "AND r.reservationStatus IN :statuses " +
           "AND DATE(r.reservationStart) = DATE(:date)")
    List<Reservation> findByUserIdAndStatusesAndDate(
            @Param("userId") String userId,
            @Param("statuses") Collection<String> statuses,
            @Param("date") LocalDateTime date);
}
