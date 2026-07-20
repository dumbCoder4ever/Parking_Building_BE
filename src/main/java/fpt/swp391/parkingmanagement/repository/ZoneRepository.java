package fpt.swp391.parkingmanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Zone;

public interface ZoneRepository extends JpaRepository<Zone, String> {
    @EntityGraph(attributePaths = {"floor", "floor.building", "floor.vehicleType"})
    Optional<Zone> findByZoneId(String zoneId);

    @EntityGraph(attributePaths = {"floor", "floor.building", "floor.vehicleType"})
    List<Zone> findByFloorFloorIdOrderByZoneNameAsc(String floorId);

    @EntityGraph(attributePaths = {"floor", "floor.building"})
    List<Zone> findByStatusIgnoreCase(String status);

    /**
     * Batch load zones for N floors in ONE query — replaces N x findByFloorFloorIdOrderByZoneNameAsc.
     * Used by BuildingService.listFloorsOfBuilding to eliminate N+1.
     */
    @EntityGraph(attributePaths = {"floor", "floor.building", "floor.vehicleType"})
    List<Zone> findByFloorFloorIdInOrderByZoneNameAsc(Collection<String> floorIds);

    boolean existsByFloorFloorIdAndZoneNameIgnoreCase(String floorId, String zoneName);

    boolean existsByFloorFloorIdAndZoneNameIgnoreCaseAndZoneIdNot(
            String floorId, String zoneName, String zoneId);

    long countByFloorFloorId(String floorId);

    long countByFloorBuildingBuildingId(String buildingId);

    List<Zone> findByFloorFloorId(String floorId);

    @EntityGraph(attributePaths = {"floor", "floor.vehicleType"})
    List<Zone> findByFloorBuildingBuildingId(String buildingId);

    List<Zone> findByFloorBuildingBuildingIdAndFloorVehicleTypeVehicleTypeId(String buildingId, String vehicleTypeId);

    @Query("SELECT z.floor.building.buildingId, COUNT(z) FROM Zone z GROUP BY z.floor.building.buildingId")
    List<Object[]> countGroupedByBuilding();

    @Query("SELECT z.floor.floorId, COUNT(z) FROM Zone z WHERE z.floor.building.buildingId = :buildingId GROUP BY z.floor.floorId")
    List<Object[]> countGroupedByFloorForBuilding(@Param("buildingId") String buildingId);
}
