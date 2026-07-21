package fpt.swp391.parkingmanagement.repository;

import fpt.swp391.parkingmanagement.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    @Query("SELECT a FROM AuditLog a WHERE "
            + "(:buildingId IS NULL OR a.buildingId = :buildingId) AND "
            + "(:actionType IS NULL OR UPPER(a.actionType) = UPPER(:actionType)) AND "
            + "(:entityType IS NULL OR UPPER(a.entityType) = UPPER(:entityType)) AND "
            + "(:fromTime IS NULL OR a.createdAt >= :fromTime) AND "
            + "(:toTime IS NULL OR a.createdAt <= :toTime) "
            + "ORDER BY a.createdAt DESC")
    Page<AuditLog> search(
            @Param("buildingId") String buildingId,
            @Param("actionType") String actionType,
            @Param("entityType") String entityType,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable);
}
