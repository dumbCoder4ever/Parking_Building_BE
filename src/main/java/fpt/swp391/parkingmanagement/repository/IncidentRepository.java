package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Incident;

public interface IncidentRepository extends JpaRepository<Incident, String> {
    List<Incident> findBySessionSessionId(String sessionId);

    // ============ DASHBOARD STATS ============

    @Query("SELECT COUNT(i) FROM Incident i WHERE UPPER(i.status) = UPPER(:status)")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.createdAt >= :from AND i.createdAt < :to")
    long countInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
