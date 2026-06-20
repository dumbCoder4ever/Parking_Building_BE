package fpt.swp391.parkingmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.ParkingSession;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, String> {
    List<ParkingSession> findByVehicleVehicleId(String vehicleId);
    Optional<ParkingSession> findByTicketTicketIdAndSessionStatus(String ticketId, String sessionStatus);

    boolean existsByVehicleVehicleIdAndSessionStatus(String vehicleId, String sessionStatus);

    boolean existsByReservationReservationIdAndSessionStatus(String reservationId, String sessionStatus);

    Optional<ParkingSession> findBySessionIdAndSessionStatus(String sessionId, String sessionStatus);

    @Query("SELECT ps FROM ParkingSession ps WHERE ps.reservation.user.userId = :userId ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findByUserIdOrderByCheckInTimeDesc(@Param("userId") String userId, Pageable pageable);

    int countByReservationUserUserId(String userId);

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation.user.userId = :userId AND ps.sessionStatus = 'COMPLETED'")
    int countCompletedByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation.user.userId = :userId AND ps.sessionStatus = 'ACTIVE'")
    int countActiveByUserId(@Param("userId") String userId);

    @Query("SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, ps.checkinTime, ps.checkoutTime)), 0) FROM ParkingSession ps WHERE ps.reservation.user.userId = :userId AND ps.sessionStatus = 'COMPLETED'")
    Long sumDurationMinutesByUserId(@Param("userId") String userId);

    @Query("SELECT ps FROM ParkingSession ps JOIN ps.reservation r WHERE r.user.userId = :userId ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findLastByUserIdOrderByCheckInTimeDesc(@Param("userId") String userId);

    @Query("SELECT ps FROM ParkingSession ps JOIN FETCH ps.reservation r JOIN FETCH ps.ticket JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building WHERE r.user.userId = :userId AND ps.sessionStatus = 'ACTIVE' ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findActiveByUserId(@Param("userId") String userId);

    @Query("SELECT ps FROM ParkingSession ps JOIN FETCH ps.reservation r JOIN FETCH ps.ticket JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building WHERE r.user.userId = :userId AND ps.sessionStatus = 'ACTIVE' ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findAllActiveByUserId(@Param("userId") String userId);

    @Query("SELECT ps FROM ParkingSession ps WHERE ps.reservation.user.userId = :userId ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findByUser(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT ps FROM ParkingSession ps JOIN FETCH ps.ticket t WHERE t.ticketCode = :ticketCode")
    Optional<ParkingSession> findByTicketCode(@Param("ticketCode") String ticketCode);

    @Query("SELECT ps FROM ParkingSession ps JOIN FETCH ps.ticket t WHERE t.ticketId = :ticketId AND ps.sessionStatus = 'ACTIVE'")
    Optional<ParkingSession> findCurrentSessionByUser(@Param("ticketId") String ticketId);

    @Query("SELECT ps FROM ParkingSession ps "
            + "JOIN FETCH ps.reservation r JOIN FETCH r.user JOIN FETCH r.vehicle v LEFT JOIN FETCH v.vehicleType "
            + "LEFT JOIN FETCH ps.ticket "
            + "WHERE ps.slot.slotId = :slotId "
            + "AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT', 'PENDING_EXIT') "
            + "ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findCurrentBySlotId(@Param("slotId") String slotId);

    @Query("SELECT ps FROM ParkingSession ps WHERE ps.vehicle.vehicleId = :vehicleId "
            + "AND ps.sessionStatus = 'ACTIVE' ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findActiveByVehicleId(@Param("vehicleId") String vehicleId);

    @Query("SELECT ps FROM ParkingSession ps WHERE ps.vehicle.vehicleId = :vehicleId "
            + "ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findLatestByVehicleId(@Param("vehicleId") String vehicleId);
}
