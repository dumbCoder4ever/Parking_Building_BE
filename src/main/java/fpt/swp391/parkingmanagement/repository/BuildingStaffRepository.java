package fpt.swp391.parkingmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
