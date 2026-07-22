package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Incident;

public interface IncidentRepository extends JpaRepository<Incident, String> {
    @EntityGraph(attributePaths = {"session", "session.ticket", "session.vehicle"})
    List<Incident> findBySessionSessionId(String sessionId);

    @EntityGraph(attributePaths = {"session", "session.ticket", "session.vehicle"})
    @Query("SELECT i FROM Incident i ORDER BY i.createdAt DESC")
    List<Incident> findAllFetchingDetails();

    @EntityGraph(attributePaths = {"session", "session.ticket", "session.vehicle"})
    List<Incident> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    /**
     * Incident list for report export. When buildingId is null, returns all buildings.
     */
    @EntityGraph(attributePaths = {"session", "session.ticket", "session.vehicle"})
    @Query("""
            SELECT i FROM Incident i
            LEFT JOIN i.session s
            LEFT JOIN s.slot sl
            LEFT JOIN sl.zone z
            LEFT JOIN z.floor f
            LEFT JOIN f.building b
            WHERE i.createdAt >= :from AND i.createdAt <= :to
              AND (:buildingId IS NULL OR b.buildingId = :buildingId)
            ORDER BY i.createdAt DESC
            """)
    List<Incident> findByCreatedAtBetweenAndBuilding(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("buildingId") String buildingId);

    // ============ DASHBOARD STATS ============

    /**
     * Aggregate incident counters. When buildingId is null, aggregates all buildings.
     */
    @Query("""
            SELECT new fpt.swp391.parkingmanagement.repository.IncidentDashboardStats(
                COALESCE(SUM(CASE WHEN UPPER(i.status) = 'OPEN' THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN UPPER(i.status) = 'IN_PROGRESS' THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN UPPER(i.status) = 'RESOLVED' THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN i.createdAt >= :from AND i.createdAt < :to THEN 1L ELSE 0L END), 0L),
                COUNT(i)
            )
            FROM Incident i
            LEFT JOIN i.session s
            LEFT JOIN s.slot sl
            LEFT JOIN sl.zone z
            LEFT JOIN z.floor f
            LEFT JOIN f.building b
            WHERE (:buildingId IS NULL OR b.buildingId = :buildingId)
            """)
    IncidentDashboardStats aggregateDashboardStats(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("buildingId") String buildingId);

    @Query("SELECT COUNT(i) FROM Incident i WHERE UPPER(i.status) = UPPER(:status)")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.createdAt >= :from AND i.createdAt < :to")
    long countInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ============ DRIVER REPORTS ============

    @EntityGraph(attributePaths = {"session", "session.ticket", "session.vehicle"})
    List<Incident> findByReporterId(String reporterId);

    @EntityGraph(attributePaths = {"session", "session.ticket", "session.vehicle"})
    @Query("SELECT i FROM Incident i WHERE i.reportSource = 'DRIVER' ORDER BY i.createdAt DESC")
    List<Incident> findAllDriverReports();

    // ============ SLOT CONFLICT CHECK ============

    @Query("SELECT i FROM Incident i WHERE i.session.slot.slotId = :slotId " +
           "AND i.incidentType = 'SLOT_CONFLICT' " +
           "AND i.status IN ('OPEN', 'IN_PROGRESS')")
    List<Incident> findActiveSlotConflictBySlotId(@Param("slotId") String slotId);

    // ============ SINGLE INCIDENT FETCH WITH FULL CHAIN ============
    // TOI UU: Lay incident voi full chain slot->zone->floor->building trong 1 query
    // Tranh N+1 khi goi getLatestReservationForIncident()

    @Query("SELECT i FROM Incident i " +
           "LEFT JOIN FETCH i.session s " +
           "LEFT JOIN FETCH s.slot slot " +
           "LEFT JOIN FETCH slot.zone z " +
           "LEFT JOIN FETCH z.floor f " +
           "LEFT JOIN FETCH f.building " +
           "LEFT JOIN FETCH s.ticket " +
           "LEFT JOIN FETCH s.vehicle v " +
           "WHERE i.incidentId = :incidentId")
    Optional<Incident> findByIdFetchingFullChain(@Param("incidentId") String incidentId);
}
