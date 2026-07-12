package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
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
            + "LEFT JOIN FETCH ps.reservation r LEFT JOIN FETCH r.user LEFT JOIN FETCH r.vehicle rv LEFT JOIN FETCH rv.vehicleType "
            + "LEFT JOIN FETCH ps.vehicle gv LEFT JOIN FETCH gv.vehicleType "
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

    Optional<ParkingSession> findFirstByReservationReservationIdOrderByCreatedAtDesc(String reservationId);

    /**
     * FIX N+1: Batch-load latest session cho nhiều reservation.
     * Native query: lấy session có createdAt MAX theo từng reservation_id.
     */
    @Query(value = """
            SELECT ps.* FROM parking_sessions ps
            INNER JOIN (
                SELECT reservation_id, MAX(created_at) AS max_created
                FROM parking_sessions
                WHERE reservation_id IN (:reservationIds)
                GROUP BY reservation_id
            ) latest ON ps.reservation_id = latest.reservation_id AND ps.created_at = latest.max_created
            """, nativeQuery = true)
    List<ParkingSession> findLatestByReservationIds(@Param("reservationIds") Collection<String> reservationIds);

    @Query("SELECT ps FROM ParkingSession ps "
            + "JOIN FETCH ps.vehicle v JOIN FETCH v.vehicleType "
            + "JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building "
            + "WHERE UPPER(v.plateNumber) = UPPER(:plateNumber) AND ps.reservation IS NULL AND ps.sessionStatus = 'ACTIVE' "
            + "ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findActiveGuestSessionsByPlateNumber(@Param("plateNumber") String plateNumber, Pageable pageable);

    default Optional<ParkingSession> findActiveGuestByPlateNumber(String plateNumber) {
        return findActiveGuestSessionsByPlateNumber(plateNumber, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("SELECT ps FROM ParkingSession ps "
            + "LEFT JOIN FETCH ps.vehicle gv "
            + "LEFT JOIN FETCH ps.reservation r LEFT JOIN FETCH r.vehicle rv "
            + "LEFT JOIN FETCH ps.ticket "
            + "LEFT JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building "
            + "WHERE ps.sessionStatus = 'ACTIVE' "
            + "AND (UPPER(gv.plateNumber) = UPPER(:plateNumber) OR UPPER(rv.plateNumber) = UPPER(:plateNumber)) "
            + "ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findActiveSessionsByPlateNumber(@Param("plateNumber") String plateNumber, Pageable pageable);

    default Optional<ParkingSession> findActiveByPlateNumber(String plateNumber) {
        return findActiveSessionsByPlateNumber(plateNumber, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("SELECT ps FROM ParkingSession ps "
            + "JOIN FETCH ps.vehicle v JOIN FETCH v.vehicleType "
            + "JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building "
            + "WHERE ps.sessionId = :sessionId AND ps.reservation IS NULL")
    Optional<ParkingSession> findGuestSessionById(@Param("sessionId") String sessionId);

    @Query("SELECT ps FROM ParkingSession ps "
            + "JOIN FETCH ps.ticket t "
            + "JOIN FETCH ps.vehicle v JOIN FETCH v.vehicleType "
            + "JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building "
            + "WHERE t.ticketCode = :ticketCode AND ps.reservation IS NULL")
    Optional<ParkingSession> findGuestSessionByTicketCode(@Param("ticketCode") String ticketCode);

    // ============ DASHBOARD STATS ============

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.sessionStatus = :status")
    long countBySessionStatus(@Param("status") String status);

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.checkinTime >= :from AND ps.checkinTime < :to")
    long countSessionsInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation IS NULL AND ps.checkinTime >= :from AND ps.checkinTime < :to")
    long countGuestSessionsInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(AVG(ps.parkingDuration), 0) FROM ParkingSession ps WHERE ps.sessionStatus = 'COMPLETED'")
    Double avgDurationMinutesCompleted();

    @Query("SELECT COALESCE(AVG(ps.totalFee), 0) FROM ParkingSession ps WHERE ps.sessionStatus = 'COMPLETED' AND ps.totalFee > 0")
    Double avgFeeCompleted();

    @Query("SELECT COUNT(DISTINCT ps.reservation.user.userId) FROM ParkingSession ps WHERE ps.sessionStatus = 'ACTIVE' AND ps.reservation IS NOT NULL")
    long countDistinctDriversCurrentlyParked();

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation IS NULL")
    long countGuestSessionsAllTime();

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation IS NOT NULL")
    long countDriverSessionsAllTime();

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation IS NULL AND ps.sessionStatus = 'ACTIVE'")
    long countActiveGuestSessions();

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation IS NOT NULL AND ps.sessionStatus = 'ACTIVE'")
    long countActiveDriverSessions();
}
