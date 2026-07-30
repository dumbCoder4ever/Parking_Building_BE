package fpt.swp391.parkingmanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.ParkingSlot;

import jakarta.persistence.LockModeType;

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

    @EntityGraph(attributePaths = {"zone", "zone.floor", "zone.floor.building"})
    @Query("SELECT ps FROM ParkingSlot ps WHERE UPPER(ps.slotStatus) = UPPER(:status)")
    List<ParkingSlot> findBySlotStatusIgnoreCaseFetchingBuilding(@Param("status") String status);

    @Query("SELECT COUNT(ps) FROM ParkingSlot ps WHERE UPPER(ps.slotStatus) = UPPER(:status)")
    long countBySlotStatus(@Param("status") String status);

    @Query("SELECT COUNT(ps) FROM ParkingSlot ps JOIN ps.zone z JOIN z.floor f JOIN f.building b WHERE b.buildingId = :buildingId")
    long countByBuildingId(@Param("buildingId") String buildingId);

    @Query("SELECT COUNT(ps) FROM ParkingSlot ps JOIN ps.zone z JOIN z.floor f JOIN f.building b WHERE b.buildingId = :buildingId AND UPPER(ps.slotStatus) = UPPER(:status)")
    long countByBuildingIdAndSlotStatus(@Param("buildingId") String buildingId, @Param("status") String status);

    /**
     * Tìm slot trống theo building + vehicleType (ưu tiên tầng thấp).
     * Dùng Pageable.ofSize(1) khi chỉ cần 1 slot để tránh load toàn bộ bảng.
     * FIX N+1: Added EntityGraph to fetch zone->floor->building chain.
     */
    @EntityGraph(attributePaths = {"zone.floor.building", "zone.floor.vehicleType"})
    @Query("SELECT ps FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f JOIN f.vehicleType vt JOIN f.building b " +
            "WHERE b.buildingId = :buildingId " +
            "AND vt.vehicleTypeId = :vehicleTypeId " +
            "AND ps.slotStatus = 'AVAILABLE' " +
            "ORDER BY f.floorLevel ASC, ps.slotName ASC")
    List<ParkingSlot> findAvailableByBuildingAndVehicleType(
            @Param("buildingId") String buildingId,
            @Param("vehicleTypeId") String vehicleTypeId,
            org.springframework.data.domain.Pageable pageable);

    default java.util.Optional<ParkingSlot> findFirstAvailableByBuildingAndVehicleType(
            String buildingId, String vehicleTypeId) {
        return findAvailableByBuildingAndVehicleType(
                buildingId, vehicleTypeId, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    @EntityGraph(attributePaths = {"zone.floor.building", "zone.floor.vehicleType"})
    @Query("SELECT ps FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f JOIN f.vehicleType vt JOIN f.building b " +
            "WHERE b.buildingId = :buildingId " +
            "AND UPPER(vt.typeName) = UPPER(:typeName) " +
            "AND ps.slotStatus = 'AVAILABLE' " +
            "ORDER BY f.floorLevel ASC, ps.slotName ASC")
    List<ParkingSlot> findAvailableByBuildingAndVehicleTypeName(
            @Param("buildingId") String buildingId,
            @Param("typeName") String typeName,
            org.springframework.data.domain.Pageable pageable);

    default java.util.Optional<ParkingSlot> findFirstAvailableByBuildingAndVehicleTypeName(
            String buildingId, String typeName) {
        return findAvailableByBuildingAndVehicleTypeName(
                buildingId, typeName, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    /**
     * Phiên bản pessimistic-write của auto-pick slot. Dùng cho walk-in flow
     * (DRIVER_WALK_IN + GUEST) để tránh 2 transaction cùng pick 1 slot khi
     * race condition xảy ra (cả 2 đều thấy slot AVAILABLE trước khi 1 bên update).
     *
     * Caller phải nằm trong @Transactional để giữ row-level lock cho tới khi commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"zone.floor.building", "zone.floor.vehicleType"})
    @Query("SELECT ps FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f JOIN f.vehicleType vt JOIN f.building b " +
            "WHERE b.buildingId = :buildingId " +
            "AND vt.vehicleTypeId = :vehicleTypeId " +
            "AND ps.slotStatus = 'AVAILABLE' " +
            "ORDER BY f.floorLevel ASC, ps.slotName ASC")
    List<ParkingSlot> lockFirstAvailableByBuildingAndVehicleType(
            @Param("buildingId") String buildingId,
            @Param("vehicleTypeId") String vehicleTypeId,
            org.springframework.data.domain.Pageable pageable);

    default java.util.Optional<ParkingSlot> lockFirstAvailableByBuildingAndVehicleType(
            String buildingId, String vehicleTypeId) {
        return lockFirstAvailableByBuildingAndVehicleType(
                buildingId, vehicleTypeId, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    /**
     * Single round-trip: aggregate slot counts per zone for a building (or all buildings).
     * Replaces N+1 count queries in availability/occupancy dashboards.
     */
    @Query("SELECT new fpt.swp391.parkingmanagement.repository.ZoneSlotCount(" +
            "z.zoneId, z.zoneName, z.status, f.floorId, f.floorName, f.floorLevel, f.status, " +
            "vt.vehicleTypeId, vt.typeName, b.buildingId, b.buildingName, b.status, " +
            "COUNT(ps), SUM(CASE WHEN UPPER(ps.slotStatus) = 'AVAILABLE' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN UPPER(ps.slotStatus) = 'RESERVED' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN UPPER(ps.slotStatus) = 'OCCUPIED' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN UPPER(ps.slotStatus) = 'PENDING_EXIT' THEN 1 ELSE 0 END)) " +
            "FROM ParkingSlot ps JOIN ps.zone z JOIN z.floor f JOIN f.vehicleType vt JOIN f.building b " +
            "WHERE (:buildingId IS NULL OR b.buildingId = :buildingId) " +
            "AND (:vehicleTypeId IS NULL OR vt.vehicleTypeId = :vehicleTypeId) " +
            "GROUP BY z.zoneId, z.zoneName, z.status, f.floorId, f.floorName, f.floorLevel, f.status, " +
            "vt.vehicleTypeId, vt.typeName, b.buildingId, b.buildingName, b.status")
    List<ZoneSlotCount> aggregateSlotCounts(@Param("buildingId") String buildingId,
                                            @Param("vehicleTypeId") String vehicleTypeId);

    /**
     * Admin dashboard occupancy: one GROUP BY building (lighter than per-zone rollup).
     * Includes buildings with zero slots via LEFT JOIN from buildings.
     */
    @Query(value = """
            SELECT b.building_id,
                   b.building_name,
                   COUNT(ps.slot_id),
                   COALESCE(SUM(CASE WHEN UPPER(ps.slot_status) = 'AVAILABLE' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN UPPER(ps.slot_status) = 'RESERVED' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN UPPER(ps.slot_status) = 'OCCUPIED' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN UPPER(ps.slot_status) = 'PENDING_EXIT' THEN 1 ELSE 0 END), 0)
            FROM buildings b
            LEFT JOIN floors f ON f.building_id = b.building_id
            LEFT JOIN zones z ON z.floor_id = f.floor_id
            LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id
            GROUP BY b.building_id, b.building_name
            ORDER BY b.building_name ASC
            """, nativeQuery = true)
    List<Object[]> aggregateOccupancyByBuildingRaw();

    default List<BuildingOccupancyCount> aggregateOccupancyByBuilding() {
        return aggregateOccupancyByBuildingRaw().stream()
                .map(row -> new BuildingOccupancyCount(
                        (String) row[0],
                        (String) row[1],
                        row[2] instanceof Number n2 ? n2.longValue() : 0L,
                        row[3] instanceof Number n3 ? n3.longValue() : 0L,
                        row[4] instanceof Number n4 ? n4.longValue() : 0L,
                        row[5] instanceof Number n5 ? n5.longValue() : 0L,
                        row[6] instanceof Number n6 ? n6.longValue() : 0L))
                .toList();
    }

    /**
     * Building picker: totals per building × vehicle type (no per-zone GROUP BY).
     */
    @Query("SELECT new fpt.swp391.parkingmanagement.repository.BuildingVtSlotCount(" +
            "b.buildingId, vt.vehicleTypeId, vt.typeName, " +
            "COUNT(ps), SUM(CASE WHEN ps.slotStatus = 'AVAILABLE' THEN 1 ELSE 0 END)) " +
            "FROM ParkingSlot ps JOIN ps.zone z JOIN z.floor f JOIN f.vehicleType vt JOIN f.building b " +
            "WHERE (:vehicleTypeId IS NULL OR vt.vehicleTypeId = :vehicleTypeId) " +
            "GROUP BY b.buildingId, vt.vehicleTypeId, vt.typeName")
    List<BuildingVtSlotCount> aggregateBuildingVtSlotCounts(@Param("vehicleTypeId") String vehicleTypeId);

    /**
     * Floor drill-down: totals per zone for one building (minimal columns).
     */
    @Query("SELECT new fpt.swp391.parkingmanagement.repository.ZoneAvailabilityCount(" +
            "z.zoneId, COUNT(ps), SUM(CASE WHEN ps.slotStatus = 'AVAILABLE' THEN 1 ELSE 0 END)) " +
            "FROM ParkingSlot ps JOIN ps.zone z JOIN z.floor f JOIN f.vehicleType vt JOIN f.building b " +
            "WHERE b.buildingId = :buildingId " +
            "AND (:vehicleTypeId IS NULL OR vt.vehicleTypeId = :vehicleTypeId) " +
            "GROUP BY z.zoneId")
    List<ZoneAvailabilityCount> aggregateZoneAvailabilityCounts(
            @Param("buildingId") String buildingId,
            @Param("vehicleTypeId") String vehicleTypeId);

    /**
     * Tim top 3 slot trong cho guest auto-assignment preview.
     * Uu tien tang thap nhat truoc.
     */
    @EntityGraph(attributePaths = {"zone.floor.building", "zone.floor.vehicleType"})
    @Query("SELECT ps FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f JOIN f.vehicleType vt JOIN f.building b " +
            "WHERE b.buildingId = :buildingId " +
            "AND vt.vehicleTypeId = :vehicleTypeId " +
            "AND ps.slotStatus = 'AVAILABLE' " +
            "ORDER BY f.floorLevel ASC, ps.slotName ASC")
    List<ParkingSlot> findTop3AvailableByBuildingAndVehicleType(
            @Param("buildingId") String buildingId,
            @Param("vehicleTypeId") String vehicleTypeId,
            org.springframework.data.domain.Pageable pageable);

    default List<ParkingSlot> findTop3AvailableByBuildingAndVehicleType(
            String buildingId, String vehicleTypeId) {
        return findAvailableByBuildingAndVehicleType(
                buildingId, vehicleTypeId, org.springframework.data.domain.PageRequest.of(0, 3));
    }

    @Query("SELECT b.buildingId, COUNT(ps) FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f JOIN f.building b " +
            "GROUP BY b.buildingId")
    List<Object[]> countGroupedByBuilding();

    @Query("SELECT ps.zone.zoneId, COUNT(ps) FROM ParkingSlot ps WHERE ps.zone.floor.floorId = :floorId GROUP BY ps.zone.zoneId")
    List<Object[]> countGroupedByZoneForFloor(@Param("floorId") String floorId);

    /**
     * Lay cac slot AVAILABLE trong cung floor, loai tru danh sach slot truyen vao.
     * Dung trong incident DRIVER_SLOT_OCCUPIED de staff chi thay slot cung floor voi reservation moi nhat.
     */
    @EntityGraph(attributePaths = {"zone", "zone.floor", "zone.floor.building"})
    @Query("SELECT ps FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f " +
            "WHERE f.floorId = :floorId " +
            "AND ps.slotStatus = 'AVAILABLE' " +
            "AND (:excludeSlotIds IS NULL OR ps.slotId NOT IN :excludeSlotIds) " +
            "ORDER BY ps.slotName ASC")
    List<ParkingSlot> findAvailableByFloorIdExcludingSlots(
            @Param("floorId") String floorId,
            @Param("excludeSlotIds") java.util.Collection<String> excludeSlotIds);

    /**
     * Lay TAT CA slot trong zone cua 1 floor, loc theo vehicle type.
     * Dung trong incident de staff xem zone goc (gom ca available va occupied de staff thay context).
     * Don gian: chi can zone + vehicleType.
     */
    @EntityGraph(attributePaths = {"zone", "zone.floor", "zone.floor.building"})
    @Query("SELECT ps FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f " +
            "WHERE f.floorId = :floorId " +
            "AND f.vehicleType.vehicleTypeId = :vehicleTypeId " +
            "ORDER BY z.zoneName ASC, ps.slotName ASC")
    List<ParkingSlot> findAllByFloorAndVehicleType(
            @Param("floorId") String floorId,
            @Param("vehicleTypeId") String vehicleTypeId);

    /**
     * Lay slot AVAILABLE (status = 'AVAILABLE') trong zone, loc theo vehicle type, loai tru current slot.
     * Dung cho DRIVER_SLOT_OCCUPIED: chi tra slot AVAILABLE cho staff chon.
     * Bo qua PENDING_EXIT, OCCUPIED, va cac status khac.
     */
    @EntityGraph(attributePaths = {"zone", "zone.floor", "zone.floor.building"})
    @Query("SELECT ps FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f " +
            "WHERE f.floorId = :floorId " +
            "AND f.vehicleType.vehicleTypeId = :vehicleTypeId " +
            "AND ps.slotStatus = 'AVAILABLE' " +
            "AND (:excludeSlotId IS NULL OR ps.slotId <> :excludeSlotId) " +
            "ORDER BY z.zoneName ASC, ps.slotName ASC")
    List<ParkingSlot> findAvailableByFloorAndVehicleType(
            @Param("floorId") String floorId,
            @Param("vehicleTypeId") String vehicleTypeId,
            @Param("excludeSlotId") String excludeSlotId);

    /**
     * Lay cac slot AVAILABLE trong building, loc theo vehicle type, loai tru danh sach slot truyen vao.
     * Dung trong incident DRIVER_SLOT_OCCUPIED de staff chi thay slot cung vehicle type (Car/Motorbike).
     *Uu tien floor co floorLevel thap hon (tang thap hon).
     */
    @EntityGraph(attributePaths = {"zone", "zone.floor", "zone.floor.building"})
    @Query("SELECT ps FROM ParkingSlot ps " +
            "JOIN ps.zone z JOIN z.floor f " +
            "WHERE f.building.buildingId = :buildingId " +
            "AND f.vehicleType.vehicleTypeId = :vehicleTypeId " +
            "AND ps.slotStatus = 'AVAILABLE' " +
            "AND (:excludeSlotIds IS NULL OR ps.slotId NOT IN :excludeSlotIds) " +
            "ORDER BY f.floorLevel ASC, ps.slotName ASC")
    List<ParkingSlot> findAvailableByBuildingAndVehicleTypeExcludingSlots(
            @Param("buildingId") String buildingId,
            @Param("vehicleTypeId") String vehicleTypeId,
            @Param("excludeSlotIds") java.util.Collection<String> excludeSlotIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE parking_slots ps
            INNER JOIN zones z ON z.zone_id = ps.zone_id
            INNER JOIN floors f ON f.floor_id = z.floor_id
            SET ps.slot_status = 'MAINTENANCE', ps.updated_at = CURRENT_TIMESTAMP(6)
            WHERE f.building_id = :buildingId
            AND UPPER(ps.slot_status) = 'AVAILABLE'
            """, nativeQuery = true)
    int bulkAvailableToMaintenanceByBuildingId(@Param("buildingId") String buildingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE parking_slots ps
            INNER JOIN zones z ON z.zone_id = ps.zone_id
            SET ps.slot_status = 'MAINTENANCE', ps.updated_at = CURRENT_TIMESTAMP(6)
            WHERE z.floor_id = :floorId
            AND UPPER(ps.slot_status) = 'AVAILABLE'
            """, nativeQuery = true)
    int bulkAvailableToMaintenanceByFloorId(@Param("floorId") String floorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE parking_slots ps
            SET ps.slot_status = 'MAINTENANCE', ps.updated_at = CURRENT_TIMESTAMP(6)
            WHERE ps.zone_id = :zoneId
            AND UPPER(ps.slot_status) = 'AVAILABLE'
            """, nativeQuery = true)
    int bulkAvailableToMaintenanceByZoneId(@Param("zoneId") String zoneId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE parking_slots ps
            INNER JOIN zones z ON z.zone_id = ps.zone_id
            INNER JOIN floors f ON f.floor_id = z.floor_id
            SET ps.slot_status = 'AVAILABLE', ps.updated_at = CURRENT_TIMESTAMP(6)
            WHERE f.building_id = :buildingId
            AND UPPER(ps.slot_status) = 'MAINTENANCE'
            """, nativeQuery = true)
    int bulkMaintenanceToAvailableByBuildingId(@Param("buildingId") String buildingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE parking_slots ps
            INNER JOIN zones z ON z.zone_id = ps.zone_id
            SET ps.slot_status = 'AVAILABLE', ps.updated_at = CURRENT_TIMESTAMP(6)
            WHERE z.floor_id = :floorId
            AND UPPER(ps.slot_status) = 'MAINTENANCE'
            """, nativeQuery = true)
    int bulkMaintenanceToAvailableByFloorId(@Param("floorId") String floorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE parking_slots ps
            SET ps.slot_status = 'AVAILABLE', ps.updated_at = CURRENT_TIMESTAMP(6)
            WHERE ps.zone_id = :zoneId
            AND UPPER(ps.slot_status) = 'MAINTENANCE'
            """, nativeQuery = true)
    int bulkMaintenanceToAvailableByZoneId(@Param("zoneId") String zoneId);

}
