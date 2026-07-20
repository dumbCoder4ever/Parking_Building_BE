package fpt.swp391.parkingmanagement.repository;

import java.util.Collection;
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

    @EntityGraph(attributePaths = {"building"})
    List<Floor> findByStatusIgnoreCase(String status);

    @EntityGraph(attributePaths = {"building", "vehicleType"})
    List<Floor> findByBuildingBuildingIdInOrderByFloorLevelAsc(Collection<String> buildingIds);

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

    @Query("""
            SELECT new fpt.swp391.parkingmanagement.repository.BuildingFloorStats(
                f.building.buildingId,
                COUNT(f),
                COALESCE(SUM(f.maxCapacity), 0),
                COALESCE(SUM(f.currentOccupancy), 0)
            )
            FROM Floor f
            GROUP BY f.building.buildingId
            """)
    java.util.List<BuildingFloorStats> aggregateStatsByBuilding();

    @Query("""
            SELECT new fpt.swp391.parkingmanagement.repository.BuildingFloorStats(
                f.building.buildingId,
                COUNT(f),
                COALESCE(SUM(f.maxCapacity), 0),
                COALESCE(SUM(f.currentOccupancy), 0)
            )
            FROM Floor f
            WHERE f.building.buildingId = :buildingId
            GROUP BY f.building.buildingId
            """)
    java.util.Optional<BuildingFloorStats> aggregateStatsForBuilding(@Param("buildingId") String buildingId);

    /**
     * One round-trip for availability floor drill-down: floors + zones + slot counts.
     * Avoids EntityGraph + separate aggregate queries (critical on remote Railway MySQL).
     */
    @Query("""
            SELECT new fpt.swp391.parkingmanagement.repository.FloorZoneAvailabilityRow(
                f.floorId, f.floorLevel, f.status, f.building.buildingId,
                vt.vehicleTypeId, vt.typeName,
                z.zoneId, z.zoneName, z.status,
                COUNT(ps.slotId),
                COALESCE(SUM(CASE WHEN ps.slotStatus = 'AVAILABLE' THEN 1 ELSE 0 END), 0)
            )
            FROM Floor f
            JOIN f.vehicleType vt
            LEFT JOIN Zone z ON z.floor = f
            LEFT JOIN ParkingSlot ps ON ps.zone = z
            WHERE f.building.buildingId = :buildingId
            AND f.status = 'ACTIVE'
            AND (:vehicleTypeId IS NULL OR vt.vehicleTypeId = :vehicleTypeId)
            GROUP BY f.floorId, f.floorLevel, f.status, f.building.buildingId,
                     vt.vehicleTypeId, vt.typeName, z.zoneId, z.zoneName, z.status
            ORDER BY f.floorLevel ASC, z.zoneName ASC
            """)
    List<FloorZoneAvailabilityRow> findFloorZoneAvailability(
            @Param("buildingId") String buildingId,
            @Param("vehicleTypeId") String vehicleTypeId);
}
