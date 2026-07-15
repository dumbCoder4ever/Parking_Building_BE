package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Floor;

public interface FloorRepository extends JpaRepository<Floor, String> {
    List<Floor> findAllByOrderByFloorLevelAsc();

    @EntityGraph(attributePaths = {"building", "vehicleType"})
    List<Floor> findByBuildingBuildingIdOrderByFloorLevelAsc(String buildingId);

    @EntityGraph(attributePaths = {"building", "vehicleType"})
    List<Floor> findByBuildingStatusIgnoreCaseOrderByBuildingBuildingNameAscFloorLevelAsc(String status);

    boolean existsByBuildingBuildingIdAndFloorLevel(String buildingId, Integer floorLevel);

    boolean existsByBuildingBuildingIdAndFloorLevelAndFloorIdNot(
            String buildingId, Integer floorLevel, String floorId);

    boolean existsByBuildingBuildingIdAndFloorNameIgnoreCase(String buildingId, String floorName);

    boolean existsByBuildingBuildingIdAndFloorNameIgnoreCaseAndFloorIdNot(
            String buildingId, String floorName, String floorId);

    boolean existsByBuildingBuildingIdAndVehicleTypeVehicleTypeId(
            String buildingId, String vehicleTypeId);

    boolean existsByBuildingBuildingIdAndVehicleTypeVehicleTypeIdAndFloorIdNot(
            String buildingId, String vehicleTypeId, String floorId);

    boolean existsByVehicleTypeVehicleTypeId(String vehicleTypeId);

    long countByBuildingBuildingId(String buildingId);

    @Query("select coalesce(sum(f.maxCapacity), 0) from Floor f where f.building.buildingId = :buildingId")
    int sumMaxCapacityByBuildingId(@Param("buildingId") String buildingId);

    @Query("select coalesce(sum(f.currentOccupancy), 0) from Floor f where f.building.buildingId = :buildingId")
    int sumCurrentOccupancyByBuildingId(@Param("buildingId") String buildingId);
}
