package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.ParkingSession;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, String> {
    List<ParkingSession> findByVehicleVehicleId(String vehicleId);
    Optional<ParkingSession> findByTicketTicketIdAndSessionStatus(String ticketId, String sessionStatus);

    Optional<ParkingSession> findByTicketTicketIdAndSessionStatusIn(String ticketId, Collection<String> sessionStatuses);

    boolean existsByVehicleVehicleIdAndSessionStatus(String vehicleId, String sessionStatus);

    boolean existsByReservationReservationIdAndSessionStatus(String reservationId, String sessionStatus);

    Optional<ParkingSession> findBySessionIdAndSessionStatus(String sessionId, String sessionStatus);

    @Query("SELECT ps FROM ParkingSession ps " +
            "JOIN FETCH ps.reservation r JOIN FETCH r.vehicle v JOIN FETCH v.vehicleType " +
            "JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building " +
            "WHERE r.user.userId = :userId ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findByUserIdOrderByCheckInTimeDesc(@Param("userId") String userId, Pageable pageable);

    int countByReservationUserUserId(String userId);

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation.user.userId = :userId AND ps.sessionStatus = 'COMPLETED'")
    int countCompletedByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation.user.userId = :userId AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT')")
    int countActiveByUserId(@Param("userId") String userId);

    @Query("SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, ps.checkinTime, ps.checkoutTime)), 0) FROM ParkingSession ps WHERE ps.reservation.user.userId = :userId AND ps.sessionStatus = 'COMPLETED'")
    Long sumDurationMinutesByUserId(@Param("userId") String userId);

    @Query("SELECT ps FROM ParkingSession ps JOIN ps.reservation r WHERE r.user.userId = :userId ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findLastByUserIdOrderByCheckInTimeDesc(@Param("userId") String userId);

    @Query("SELECT ps FROM ParkingSession ps JOIN FETCH ps.reservation r JOIN FETCH r.vehicle v JOIN FETCH v.vehicleType " +
            "JOIN FETCH ps.ticket JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building " +
            "WHERE r.user.userId = :userId AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findActiveByUserId(@Param("userId") String userId);

    @Query("SELECT ps FROM ParkingSession ps JOIN FETCH ps.reservation r JOIN FETCH r.vehicle v JOIN FETCH v.vehicleType " +
            "JOIN FETCH ps.ticket JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building " +
            "WHERE r.user.userId = :userId AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findAllActiveByUserId(@Param("userId") String userId);

    @Query("SELECT ps FROM ParkingSession ps WHERE ps.reservation.user.userId = :userId ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findByUser(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT ps FROM ParkingSession ps JOIN FETCH ps.ticket t WHERE t.ticketCode = :ticketCode")
    Optional<ParkingSession> findByTicketCode(@Param("ticketCode") String ticketCode);

    @Query("SELECT ps FROM ParkingSession ps JOIN FETCH ps.ticket t WHERE t.ticketId = :ticketId AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT')")
    Optional<ParkingSession> findCurrentSessionByUser(@Param("ticketId") String ticketId);

    /**
     * FIX N+1: Full graph fetch for checkout operations.
     * Loads slot->zone->floor->building chain and vehicle->vehicleType in one query.
     */
    @Query("SELECT ps FROM ParkingSession ps "
            + "LEFT JOIN FETCH ps.slot s LEFT JOIN FETCH s.zone z LEFT JOIN FETCH z.floor f LEFT JOIN FETCH f.building "
            + "LEFT JOIN FETCH s.zone.floor.vehicleType "
            + "LEFT JOIN FETCH ps.vehicle gv LEFT JOIN FETCH gv.vehicleType "
            + "LEFT JOIN FETCH ps.reservation r LEFT JOIN FETCH r.user LEFT JOIN FETCH r.vehicle rv LEFT JOIN FETCH rv.vehicleType "
            + "LEFT JOIN FETCH ps.ticket t "
            + "WHERE t.ticketId = :ticketId AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT')")
    Optional<ParkingSession> findActiveSessionGraphByTicketId(@Param("ticketId") String ticketId);

    /**
     * FIX N+1: Full graph fetch for confirmExitAndCheckout operation.
     */
    @Query("SELECT ps FROM ParkingSession ps "
            + "LEFT JOIN FETCH ps.slot s LEFT JOIN FETCH s.zone z LEFT JOIN FETCH z.floor f LEFT JOIN FETCH f.building "
            + "LEFT JOIN FETCH s.zone.floor.vehicleType "
            + "LEFT JOIN FETCH ps.vehicle gv LEFT JOIN FETCH gv.vehicleType "
            + "LEFT JOIN FETCH ps.reservation r LEFT JOIN FETCH r.user LEFT JOIN FETCH r.vehicle rv LEFT JOIN FETCH rv.vehicleType "
            + "LEFT JOIN FETCH ps.ticket t "
            + "WHERE ps.sessionId = :sessionId")
    Optional<ParkingSession> findByIdGraph(@Param("sessionId") String sessionId);

    /**
     * FIX N+1: Full graph for checkin via ticket code.
     * Loads ticket, reservation, vehicle, slot chain in one query.
     */
    @Query("SELECT ps FROM ParkingSession ps "
            + "JOIN FETCH ps.ticket t "
            + "JOIN FETCH ps.reservation r "
            + "JOIN FETCH r.vehicle rv JOIN FETCH rv.vehicleType "
            + "JOIN FETCH r.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building "
            + "JOIN FETCH r.user "
            + "JOIN FETCH ps.vehicle gv LEFT JOIN FETCH gv.vehicleType "
            + "WHERE t.ticketCode = :ticketCode")
    Optional<ParkingSession> findByTicketCodeGraph(@Param("ticketCode") String ticketCode);

    @Query("SELECT ps FROM ParkingSession ps "
            + "LEFT JOIN FETCH ps.reservation r LEFT JOIN FETCH r.user LEFT JOIN FETCH r.vehicle rv LEFT JOIN FETCH rv.vehicleType "
            + "LEFT JOIN FETCH ps.vehicle gv LEFT JOIN FETCH gv.vehicleType "
            + "LEFT JOIN FETCH ps.ticket "
            + "WHERE ps.slot.slotId = :slotId "
            + "AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT', 'PENDING_EXIT') "
            + "ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findCurrentBySlotId(@Param("slotId") String slotId);

    /**
     * FIX N+1: Batch-load slot IDs có active session trong 1 query.
     * Trước đây gọi findCurrentBySlotId trong loop → mỗi slot 1 query.
     */
    @Query("SELECT DISTINCT ps.slot.slotId FROM ParkingSession ps WHERE ps.slot.slotId IN :slotIds AND ps.sessionStatus IN :statuses")
    Set<String> findSlotIdsBySlotIdInAndSessionStatusIn(
            @Param("slotIds") Collection<String> slotIds,
            @Param("statuses") Collection<String> statuses);

    @Query("SELECT ps FROM ParkingSession ps WHERE ps.vehicle.vehicleId = :vehicleId "
            + "AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findActiveByVehicleId(@Param("vehicleId") String vehicleId);

    @Query("SELECT ps FROM ParkingSession ps WHERE ps.vehicle.vehicleId = :vehicleId "
            + "ORDER BY ps.checkinTime DESC limit 1")
    Optional<ParkingSession> findLatestByVehicleId(@Param("vehicleId") String vehicleId);

    /**
     * Batch-load active sessions for many vehicles (manager vehicle search).
     */
    @Query("SELECT ps FROM ParkingSession ps JOIN FETCH ps.vehicle WHERE ps.vehicle.vehicleId IN :vehicleIds "
            + "AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT')")
    List<ParkingSession> findActiveByVehicleIds(@Param("vehicleIds") Collection<String> vehicleIds);

    /**
     * Batch-load latest session per vehicle (by checkin_time).
     */
    @Query("""
            SELECT ps FROM ParkingSession ps JOIN FETCH ps.vehicle v
            WHERE v.vehicleId IN :vehicleIds
            AND ps.checkinTime = (
                SELECT MAX(ps2.checkinTime) FROM ParkingSession ps2
                WHERE ps2.vehicle.vehicleId = v.vehicleId
            )
            """)
    List<ParkingSession> findLatestByVehicleIds(@Param("vehicleIds") Collection<String> vehicleIds);

    Optional<ParkingSession> findFirstByReservationReservationIdOrderByCreatedAtDesc(String reservationId);

    /**
     * FIX N+1: Batch-load latest session cho nhiều reservation.
     * Dùng JPQL thay vì native query để tránh duplicates khi session có cùng createdAt.
     * Chỉ lấy 1 session mới nhất cho mỗi reservation.
     */
    @Query("""
            SELECT ps FROM ParkingSession ps
            LEFT JOIN FETCH ps.ticket
            WHERE ps.reservation.reservationId IN :reservationIds
            AND ps.createdAt = (
                SELECT MAX(ps2.createdAt) FROM ParkingSession ps2
                WHERE ps2.reservation.reservationId = ps.reservation.reservationId
            )
            """)
    List<ParkingSession> findLatestByReservationIds(@Param("reservationIds") Collection<String> reservationIds);

    @Query("SELECT ps FROM ParkingSession ps "
            + "JOIN FETCH ps.vehicle v JOIN FETCH v.vehicleType "
            + "JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building "
            + "WHERE UPPER(v.plateNumber) = UPPER(:plateNumber) AND ps.reservation IS NULL AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') "
            + "ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findActiveGuestSessionsByPlateNumber(@Param("plateNumber") String plateNumber, Pageable pageable);

    default Optional<ParkingSession> findActiveGuestByPlateNumber(String plateNumber) {
        return findActiveGuestSessionsByPlateNumber(plateNumber, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("SELECT ps FROM ParkingSession ps "
            + "JOIN FETCH ps.vehicle v JOIN FETCH v.vehicleType "
            + "JOIN FETCH ps.ticket "
            + "JOIN FETCH ps.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building "
            + "WHERE v.vehicleId = :vehicleId AND ps.reservation IS NULL "
            + "AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') "
            + "ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findActiveGuestSessionsByVehicleId(@Param("vehicleId") String vehicleId, Pageable pageable);

    default Optional<ParkingSession> findActiveGuestByVehicleId(String vehicleId) {
        return findActiveGuestSessionsByVehicleId(vehicleId, PageRequest.of(0, 1)).stream().findFirst();
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
            + "LEFT JOIN FETCH ps.vehicle gv "
            + "LEFT JOIN FETCH ps.reservation r LEFT JOIN FETCH r.vehicle rv "
            + "LEFT JOIN FETCH ps.ticket "
            + "WHERE ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') "
            + "AND ((gv IS NOT NULL AND gv.vehicleId = :vehicleId) "
            + "OR (rv IS NOT NULL AND rv.vehicleId = :vehicleId)) "
            + "ORDER BY ps.checkinTime DESC")
    List<ParkingSession> findActiveSessionsIncludingReservationByVehicleId(
            @Param("vehicleId") String vehicleId, Pageable pageable);

    default Optional<ParkingSession> findAnyActiveSessionByVehicleId(String vehicleId) {
        return findActiveSessionsIncludingReservationByVehicleId(vehicleId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
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

    /**
     * One table scan for admin dashboard session counters + avg duration/fee.
     */
    @Query("""
            SELECT new fpt.swp391.parkingmanagement.repository.SessionDashboardStats(
                COALESCE(SUM(CASE WHEN ps.sessionStatus = 'ACTIVE' THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN ps.checkinTime >= :startOfToday AND ps.checkinTime < :endOfToday THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN ps.checkinTime >= :startOfMonth AND ps.checkinTime < :now THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN ps.reservation IS NULL AND ps.checkinTime >= :startOfToday AND ps.checkinTime < :endOfToday THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN ps.reservation IS NULL THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN ps.reservation IS NOT NULL THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN ps.reservation IS NULL AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN ps.reservation IS NOT NULL AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') THEN 1L ELSE 0L END), 0L),
                AVG(CASE WHEN ps.sessionStatus = 'COMPLETED' THEN ps.parkingDuration ELSE NULL END),
                AVG(CASE WHEN ps.sessionStatus = 'COMPLETED' AND ps.totalFee > 0 THEN ps.totalFee ELSE NULL END)
            )
            FROM ParkingSession ps
            """)
    SessionDashboardStats aggregateDashboardSessionCounts(
            @Param("startOfToday") LocalDateTime startOfToday,
            @Param("endOfToday") LocalDateTime endOfToday,
            @Param("startOfMonth") LocalDateTime startOfMonth,
            @Param("now") LocalDateTime now);

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

    @Query("SELECT COUNT(DISTINCT ps.reservation.user.userId) FROM ParkingSession ps WHERE ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') AND ps.reservation IS NOT NULL")
    long countDistinctDriversCurrentlyParked();

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation IS NULL")
    long countGuestSessionsAllTime();

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation IS NOT NULL")
    long countDriverSessionsAllTime();

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation IS NULL AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT')")
    long countActiveGuestSessions();

    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.reservation IS NOT NULL AND ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT')")
    long countActiveDriverSessions();

    /**
     * Peak-hour analysis: check-in counts grouped by hour of day.
     * When buildingId is null, aggregates all buildings.
     */
    @Query("""
            SELECT HOUR(ps.checkinTime), COUNT(ps)
            FROM ParkingSession ps
            JOIN ps.slot s JOIN s.zone z JOIN z.floor f JOIN f.building b
            WHERE ps.checkinTime >= :from AND ps.checkinTime <= :to
              AND (:buildingId IS NULL OR b.buildingId = :buildingId)
            GROUP BY HOUR(ps.checkinTime)
            """)
    List<Object[]> countCheckinsByHour(
            @Param("buildingId") String buildingId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Active sessions for scheduler jobs (overstay / payment reminder).
     */
    @Query("""
            SELECT DISTINCT ps FROM ParkingSession ps
            LEFT JOIN FETCH ps.slot s
            LEFT JOIN FETCH s.zone z
            LEFT JOIN FETCH z.floor f
            LEFT JOIN FETCH f.building b
            LEFT JOIN FETCH ps.vehicle v
            LEFT JOIN FETCH ps.reservation r
            LEFT JOIN FETCH r.user u
            LEFT JOIN FETCH ps.ticket t
            WHERE ps.sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT')
              AND ps.checkinTime IS NOT NULL
              AND ps.checkinTime <= :cutoff
            """)
    List<ParkingSession> findActiveSessionsCheckedInBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            SELECT DISTINCT ps FROM ParkingSession ps
            LEFT JOIN FETCH ps.slot s
            LEFT JOIN FETCH s.zone z
            LEFT JOIN FETCH z.floor f
            LEFT JOIN FETCH f.building b
            LEFT JOIN FETCH ps.vehicle v
            LEFT JOIN FETCH ps.reservation r
            LEFT JOIN FETCH r.user u
            LEFT JOIN FETCH ps.ticket t
            WHERE ps.sessionStatus = 'PENDING_PAYMENT'
              AND ps.checkinTime IS NOT NULL
              AND ps.checkinTime <= :cutoff
            """)
    List<ParkingSession> findPendingPaymentSessionsBefore(@Param("cutoff") LocalDateTime cutoff);
}
