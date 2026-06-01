package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Floor;

public interface FloorRepository extends JpaRepository<Floor, String> {
    List<Floor> findAllByOrderByFloorLevelAsc();

    boolean existsByBuildingBuildingIdAndFloorLevel(String buildingId, Integer floorLevel);

    boolean existsByBuildingBuildingIdAndFloorNameIgnoreCase(String buildingId, String floorName);

    long countByBuildingBuildingId(String buildingId);

    @Query("select coalesce(sum(f.maxCapacity), 0) from Floor f where f.building.buildingId = :buildingId")
    int sumMaxCapacityByBuildingId(@Param("buildingId") String buildingId);
}
