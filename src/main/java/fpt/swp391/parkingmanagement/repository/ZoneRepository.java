package fpt.swp391.parkingmanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE zones z
            INNER JOIN floors f ON f.floor_id = z.floor_id
            SET z.status = :status, z.updated_at = CURRENT_TIMESTAMP(6)
            WHERE f.building_id = :buildingId
            AND (z.status IS NULL OR UPPER(z.status) <> UPPER(:status))
            """, nativeQuery = true)
    int bulkUpdateStatusByBuildingId(
            @Param("buildingId") String buildingId,
            @Param("status") String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE zones z
            SET z.status = :status, z.updated_at = CURRENT_TIMESTAMP(6)
            WHERE z.floor_id = :floorId
            AND (z.status IS NULL OR UPPER(z.status) <> UPPER(:status))
            """, nativeQuery = true)
    int bulkUpdateStatusByFloorId(
            @Param("floorId") String floorId,
            @Param("status") String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE zones z
            INNER JOIN floors f ON f.floor_id = z.floor_id
            SET z.status = 'ACTIVE', z.updated_at = CURRENT_TIMESTAMP(6)
            WHERE f.building_id = :buildingId
            AND UPPER(z.status) IN ('MAINTENANCE', 'INACTIVE')
            """, nativeQuery = true)
    int bulkReopenClosedByBuildingId(@Param("buildingId") String buildingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE zones z
            SET z.status = 'ACTIVE', z.updated_at = CURRENT_TIMESTAMP(6)
            WHERE z.floor_id = :floorId
            AND UPPER(z.status) IN ('MAINTENANCE', 'INACTIVE')
            """, nativeQuery = true)
    int bulkReopenClosedByFloorId(@Param("floorId") String floorId);

    /**
     * After reopening closed zones to ACTIVE: mark FULL when zone has slots but none AVAILABLE.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE zones z
            INNER JOIN floors f ON f.floor_id = z.floor_id
            SET z.status = 'FULL', z.updated_at = CURRENT_TIMESTAMP(6)
            WHERE f.building_id = :buildingId
            AND UPPER(z.status) = 'ACTIVE'
            AND EXISTS (SELECT 1 FROM parking_slots ps WHERE ps.zone_id = z.zone_id)
            AND NOT EXISTS (
                SELECT 1 FROM parking_slots ps
                WHERE ps.zone_id = z.zone_id AND UPPER(ps.slot_status) = 'AVAILABLE'
            )
            """, nativeQuery = true)
    int bulkMarkFullWhenNoAvailableByBuildingId(@Param("buildingId") String buildingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE zones z
            SET z.status = 'FULL', z.updated_at = CURRENT_TIMESTAMP(6)
            WHERE z.floor_id = :floorId
            AND UPPER(z.status) = 'ACTIVE'
            AND EXISTS (SELECT 1 FROM parking_slots ps WHERE ps.zone_id = z.zone_id)
            AND NOT EXISTS (
                SELECT 1 FROM parking_slots ps
                WHERE ps.zone_id = z.zone_id AND UPPER(ps.slot_status) = 'AVAILABLE'
            )
            """, nativeQuery = true)
    int bulkMarkFullWhenNoAvailableByFloorId(@Param("floorId") String floorId);
}
