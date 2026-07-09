package fpt.swp391.parkingmanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.ParkingSlot;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, String> {

    @Query("select ps from ParkingSlot ps join ps.zone z join z.floor f join f.vehicleType vt "
            + "where vt.vehicleTypeId = :vehicleTypeId and ps.slotStatus = 'AVAILABLE'")
    List<ParkingSlot> findAvailableByVehicleType(@Param("vehicleTypeId") String vehicleTypeId);

    @Query("select ps from ParkingSlot ps join ps.zone z join z.floor f "
            + "where f.floorId = :floorId and f.vehicleType.vehicleTypeId = :vehicleTypeId and ps.slotStatus = 'AVAILABLE'")
    List<ParkingSlot> findAvailableByFloorAndVehicleType(@Param("floorId") String floorId, @Param("vehicleTypeId") String vehicleTypeId);

    @Query("select ps from ParkingSlot ps join ps.zone z join z.floor f join f.building b where b.buildingId = :buildingId")
    List<ParkingSlot> findByZoneFloorBuildingBuildingId(@Param("buildingId") String buildingId);

    @Query("select count(ps) from ParkingSlot ps join ps.zone z join z.floor f "
            + "where f.floorId = :floorId and f.vehicleType.vehicleTypeId = :vehicleTypeId and ps.slotStatus = 'AVAILABLE'")
    long countAvailableByFloorAndVehicleType(@Param("floorId") String floorId, @Param("vehicleTypeId") String vehicleTypeId);

    @Query("select ps from ParkingSlot ps join ps.zone z join z.floor f join f.vehicleType vt "
            + "where vt.vehicleTypeId = :vehicleTypeId and ps.slotStatus = 'AVAILABLE'")
    Optional<ParkingSlot> findFirstAvailableByVehicleType(@Param("vehicleTypeId") String vehicleTypeId);

    long countByZoneZoneId(String zoneId);

    long countByZoneZoneIdAndSlotStatusIgnoreCase(String zoneId, String slotStatus);

    @EntityGraph(attributePaths = {"zone", "zone.floor", "zone.floor.building", "zone.floor.vehicleType"})
    List<ParkingSlot> findByZoneZoneIdOrderBySlotNameAsc(String zoneId);

    /**
     * FIX N+1: Batch-load slots cho nhiều zones trong 1 query thay vì loop từng zone.
     * Dùng trong ReservationService.getAvailability().
     */
    @EntityGraph(attributePaths = {"zone", "zone.floor", "zone.floor.building", "zone.floor.vehicleType"})
    List<ParkingSlot> findByZoneZoneIdInOrderBySlotNameAsc(Collection<String> zoneIds);

    @EntityGraph(attributePaths = {"zone", "zone.floor", "zone.floor.building", "zone.floor.vehicleType"})
    Optional<ParkingSlot> findBySlotId(String slotId);

    boolean existsByZoneZoneIdAndSlotNameIgnoreCase(String zoneId, String slotName);

    Optional<ParkingSlot> findFirstByZoneZoneIdOrderBySlotNameAsc(String zoneId);

    List<ParkingSlot> findBySlotStatusIgnoreCase(String slotStatus);

    @Query("SELECT COUNT(ps) FROM ParkingSlot ps WHERE UPPER(ps.slotStatus) = UPPER(:status)")
    long countBySlotStatus(@Param("status") String status);

    @Query("SELECT COUNT(ps) FROM ParkingSlot ps JOIN ps.zone z JOIN z.floor f JOIN f.building b WHERE b.buildingId = :buildingId")
    long countByBuildingId(@Param("buildingId") String buildingId);

    @Query("SELECT COUNT(ps) FROM ParkingSlot ps JOIN ps.zone z JOIN z.floor f JOIN f.building b WHERE b.buildingId = :buildingId AND UPPER(ps.slotStatus) = UPPER(:status)")
    long countByBuildingIdAndSlotStatus(@Param("buildingId") String buildingId, @Param("status") String status);

    /**
     * Tìm slot trống đầu tiên theo building + vehicleType.
     * Dùng trong quick guest checkin: staff quét biển số → hệ thống tự assign slot.
     * Order by floorLevel ASC (ưu tiên tầng thấp), rồi slotName ASC.
     */
    @Query("SELECT ps FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f JOIN f.vehicleType vt JOIN f.building b " +
            "WHERE b.buildingId = :buildingId " +
            "AND vt.vehicleTypeId = :vehicleTypeId " +
            "AND ps.slotStatus = 'AVAILABLE' " +
            "ORDER BY f.floorLevel ASC, ps.slotName ASC")
    List<ParkingSlot> findAvailableByBuildingAndVehicleType(
            @Param("buildingId") String buildingId,
            @Param("vehicleTypeId") String vehicleTypeId);


    /**
     * Single round-trip: aggregate slot counts per zone for a building (or all buildings).
     * Replaces N+1 count queries in availability/occupancy dashboards.
     */
    @Query("SELECT new fpt.swp391.parkingmanagement.repository.ZoneSlotCount(" +
            "z.zoneId, z.zoneName, z.status, f.floorId, f.floorName, f.floorLevel, f.status, " +
            "vt.vehicleTypeId, vt.typeName, b.buildingId, b.buildingName, b.status, " +
            "COUNT(ps), SUM(CASE WHEN UPPER(ps.slotStatus) = 'AVAILABLE' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN UPPER(ps.slotStatus) = 'RESERVED' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN UPPER(ps.slotStatus) = 'OCCUPIED' THEN 1 ELSE 0 END)) " +
            "FROM ParkingSlot ps JOIN ps.zone z JOIN z.floor f JOIN f.vehicleType vt JOIN f.building b " +
            "WHERE (:buildingId IS NULL OR b.buildingId = :buildingId) " +
            "AND (:vehicleTypeId IS NULL OR vt.vehicleTypeId = :vehicleTypeId) " +
            "GROUP BY z.zoneId, z.zoneName, z.status, f.floorId, f.floorName, f.floorLevel, f.status, " +
            "vt.vehicleTypeId, vt.typeName, b.buildingId, b.buildingName, b.status")
    List<ZoneSlotCount> aggregateSlotCounts(@Param("buildingId") String buildingId,
                                            @Param("vehicleTypeId") String vehicleTypeId);

}
