package fpt.swp391.parkingmanagement.repository;

import fpt.swp391.parkingmanagement.entity.BuildingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BuildingRuleRepository extends JpaRepository<BuildingRule, String> {

    List<BuildingRule> findByBuildingBuildingIdOrderByCreatedAtDesc(String buildingId);

    @Query("SELECT r FROM BuildingRule r WHERE r.building.buildingId = :buildingId "
            + "AND UPPER(r.status) = 'ACTIVE' ORDER BY r.createdAt ASC")
    List<BuildingRule> findActiveByBuildingId(@Param("buildingId") String buildingId);

    boolean existsByBuildingBuildingIdAndRuleCodeIgnoreCaseAndStatusIgnoreCase(
            String buildingId, String ruleCode, String status);
}
