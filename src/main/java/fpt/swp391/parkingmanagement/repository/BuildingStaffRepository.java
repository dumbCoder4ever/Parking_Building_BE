package fpt.swp391.parkingmanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.BuildingStaff;

public interface BuildingStaffRepository extends JpaRepository<BuildingStaff, String> {

    boolean existsByBuildingBuildingIdAndUserUserId(String buildingId, String userId);

    @EntityGraph(attributePaths = {"user"})
    List<BuildingStaff> findByBuildingBuildingIdOrderByAssignedAtDesc(String buildingId);

    @EntityGraph(attributePaths = {"building"})
    List<BuildingStaff> findByUserUserIdOrderByAssignedAtDesc(String userId);

    Optional<BuildingStaff> findByBuildingBuildingIdAndUserUserId(String buildingId, String userId);

    void deleteByBuildingBuildingIdAndUserUserId(String buildingId, String userId);

    long countByUserUserId(String userId);

    long countByBuildingBuildingId(String buildingId);

    @Query("SELECT bs.building.buildingId FROM BuildingStaff bs WHERE bs.user.userId = :userId")
    List<String> findBuildingIdsByUserId(@Param("userId") String userId);

    /**
     * Batch-load building assignments for many staff users (avoids N+1 in getAllStaff).
     * Returns [userId, buildingId] rows.
     */
    @Query("""
            SELECT bs.user.userId, bs.building.buildingId
            FROM BuildingStaff bs
            WHERE bs.user.userId IN :userIds
            ORDER BY bs.assignedAt DESC
            """)
    List<Object[]> findBuildingIdsByUserIds(@Param("userIds") Collection<String> userIds);

    @EntityGraph(attributePaths = {"building"})
    List<BuildingStaff> findByUserUserId(String userId);
}
