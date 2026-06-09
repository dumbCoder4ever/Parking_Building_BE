package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Floor;

public interface FloorRepository extends JpaRepository<Floor, String> {
    List<Floor> findAllByOrderByFloorLevelAsc();

    List<Floor> findByBuildingBuildingIdOrderByFloorLevelAsc(String buildingId);

    boolean existsByBuildingBuildingIdAndFloorLevel(String buildingId, Integer floorLevel);

    boolean existsByBuildingBuildingIdAndFloorLevelAndFloorIdNot(
            String buildingId, Integer floorLevel, String floorId);

    boolean existsByBuildingBuildingIdAndFloorNameIgnoreCase(String buildingId, String floorName);

    boolean existsByBuildingBuildingIdAndFloorNameIgnoreCaseAndFloorIdNot(
            String buildingId, String floorName, String floorId);

    boolean existsByBuildingBuildingIdAndVehicleTypeVehicleTypeId(
            String buildingId, String vehicleTypeId);

    boolean existsByVehicleTypeVehicleTypeId(String vehicleTypeId);

    long countByBuildingBuildingId(String buildingId);

    @Query("select coalesce(sum(f.maxCapacity), 0) from Floor f where f.building.buildingId = :buildingId")
    int sumMaxCapacityByBuildingId(@Param("buildingId") String buildingId);
}
