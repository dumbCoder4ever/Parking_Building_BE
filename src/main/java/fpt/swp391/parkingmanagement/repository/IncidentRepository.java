package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.List;

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

    // ============ DASHBOARD STATS ============

    @Query("""
            SELECT new fpt.swp391.parkingmanagement.repository.IncidentDashboardStats(
                COALESCE(SUM(CASE WHEN UPPER(i.status) = 'OPEN' THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN i.createdAt >= :from AND i.createdAt < :to THEN 1L ELSE 0L END), 0L),
                COUNT(i)
            )
            FROM Incident i
            """)
    IncidentDashboardStats aggregateDashboardStats(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

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
}
