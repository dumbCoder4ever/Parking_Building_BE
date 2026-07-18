package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fpt.swp391.parkingmanagement.entity.Reservation;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    boolean existsByVehicleVehicleIdAndReservationStatusIn(String vehicleId, Collection<String> statuses);

    Optional<Reservation> findByReservationCode(String reservationCode);

    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user", "vehicle.vehicleType"})
    Optional<Reservation> findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc(
            String slotId, Collection<String> statuses);

    /**
     * FIX N+1: Batch-load active reservations cho nhiều slot trong 1 query.
     * Trước đây gọi findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc
     * trong loop → mỗi slot 1 query.
     */
    @EntityGraph(attributePaths = {"vehicle", "user", "vehicle.vehicleType"})
    List<Reservation> findBySlotSlotIdInAndReservationStatusInOrderByCreatedAtDesc(
            Collection<String> slotIds, Collection<String> statuses);

    /**
     * FIX N+1: Chỉ lấy slotIds của reservations active - tránh load toàn bộ Reservation.
     * Dùng trong SlotStatusSyncJob để check orphaned slots.
     */
    @Query("SELECT DISTINCT r.slot.slotId FROM Reservation r WHERE r.slot IS NOT NULL AND r.reservationStatus IN :statuses")
    List<String> findActiveSlotIdsByStatuses(@Param("statuses") Collection<String> statuses);

    /**
     * Batch-load active reservations for all zones in 1 query.
     * Dùng trong getAvailability() để loại bỏ N+1 reservation lookup.
     */
    @Query("SELECT r FROM Reservation r JOIN r.slot s JOIN s.zone z " +
           "JOIN FETCH r.vehicle JOIN FETCH r.user JOIN FETCH r.vehicle.vehicleType " +
           "WHERE z.zoneId IN :zoneIds AND r.reservationStatus IN :statuses " +
           "ORDER BY r.createdAt DESC")
    @EntityGraph(attributePaths = {"slot", "vehicle", "user"})
    List<Reservation> findBySlotZoneIdInAndReservationStatusInOrderByCreatedAtDesc(
            @Param("zoneIds") Collection<String> zoneIds,
            @Param("statuses") Collection<String> statuses);

    @Query("SELECT r FROM Reservation r " +
           "JOIN FETCH r.slot s " +
           "JOIN FETCH s.zone z " +
           "JOIN FETCH z.floor f " +
           "JOIN FETCH f.building " +
           "JOIN FETCH r.vehicle v " +
           "JOIN FETCH v.vehicleType " +
           "JOIN FETCH r.user " +
           "WHERE r.user.userId = :userId " +
           "ORDER BY r.createdAt DESC")
    List<Reservation> findByUserUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    // NOTE: findOverlappingReservations và findOverlappingReservationsByVehicleType đã xóa
    // vì không còn reservationEnd nữa - logic overlap đã thay đổi

    @Query(value = "SELECT r.* FROM reservations r " +
           "JOIN parking_slots ps ON r.slot_id = ps.slot_id " +
           "WHERE r.reservation_status IN ('PENDING', 'APPROVED') " +
           "AND ps.slot_status = 'RESERVED' " +
           // Expire reservations: reservationStart + gracePeriod <= now
           "AND DATE_ADD(r.reservation_start, INTERVAL r.grace_period_minutes MINUTE) <= :currentTime",
           nativeQuery = true)
    List<Reservation> findExpiredReservations(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Sargable-friendly expire lookup: filter PENDING by reservation_start first
     * (usable with idx on status+start), then apply grace in the DATE_ADD predicate.
     * JOIN FETCH slot/user via EntityGraph is not available on native queries —
     * service batches with saveAll after loading relations once.
     */
    @Query(value = "SELECT r.* FROM reservations r " +
           "WHERE r.reservation_status = 'PENDING' " +
           "AND r.reservation_start <= :currentTime " +
           "AND DATE_ADD(r.reservation_start, INTERVAL r.grace_period_minutes MINUTE) <= :currentTime",
           nativeQuery = true)
    List<Reservation> findExpiredPendingReservations(@Param("currentTime") LocalDateTime currentTime);

    @Query(value = "SELECT r.* FROM reservations r " +
           "WHERE r.reservation_status = 'APPROVED' " +
           // APPROVED reservations expire: reservationStart + gracePeriod <= now
           "AND DATE_ADD(r.reservation_start, INTERVAL r.grace_period_minutes MINUTE) <= :currentTime",
           nativeQuery = true)
    List<Reservation> findExpiredApprovedReservations(@Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT r FROM Reservation r WHERE r.user.userId = :userId " +
           "AND r.reservationStatus IN :statuses " +
           "AND DATE(r.reservationStart) = DATE(:date)")
    List<Reservation> findByUserIdAndStatusesAndDate(
            @Param("userId") String userId,
            @Param("statuses") Collection<String> statuses,
            @Param("date") LocalDateTime date);

    // ============ STAFF APIs ============

    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    List<Reservation> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    List<Reservation> findByReservationStatusOrderByCreatedAtDesc(String status);

    @Query(value = "SELECT r FROM Reservation r JOIN r.slot s JOIN s.zone z JOIN z.floor f " +
           "WHERE f.building.buildingId = :buildingId ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM Reservation r JOIN r.slot s JOIN s.zone z JOIN z.floor f " +
           "WHERE f.building.buildingId = :buildingId")
    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    List<Reservation> findByBuildingBuildingIdOrderByCreatedAtDesc(@Param("buildingId") String buildingId);

    @Query(value = "SELECT r FROM Reservation r JOIN r.slot s JOIN s.zone z JOIN z.floor f " +
           "WHERE f.building.buildingId = :buildingId ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM Reservation r JOIN r.slot s JOIN s.zone z JOIN z.floor f " +
           "WHERE f.building.buildingId = :buildingId")
    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    org.springframework.data.domain.Page<Reservation> findByBuildingBuildingIdOrderByCreatedAtDesc(
            @Param("buildingId") String buildingId, Pageable pageable);

    @Query(value = "SELECT r FROM Reservation r JOIN r.slot s JOIN s.zone z JOIN z.floor f " +
           "WHERE f.building.buildingId = :buildingId AND r.reservationStatus = :status ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM Reservation r JOIN r.slot s JOIN s.zone z JOIN z.floor f " +
           "WHERE f.building.buildingId = :buildingId AND r.reservationStatus = :status")
    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    List<Reservation> findByBuildingBuildingIdAndReservationStatusOrderByCreatedAtDesc(
            @Param("buildingId") String buildingId, @Param("status") String reservationStatus);

    @Query("SELECT r FROM Reservation r JOIN r.slot s JOIN s.zone z JOIN z.floor f " +
           "WHERE f.building.buildingId = :buildingId AND r.reservationId = :reservationId")
    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    Optional<Reservation> findByBuildingBuildingIdAndReservationId(
            @Param("buildingId") String buildingId, @Param("reservationId") String reservationId);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.slot s JOIN FETCH s.zone z " +
           "JOIN FETCH z.floor f JOIN FETCH f.building " +
           "JOIN FETCH r.vehicle JOIN FETCH r.user " +
           "WHERE f.building.buildingId = :buildingId AND r.reservationCode = :reservationCode")
    Optional<Reservation> findByBuildingBuildingIdAndReservationCodeFetchingDetails(
            @Param("buildingId") String buildingId,
            @Param("reservationCode") String reservationCode);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.slot s JOIN FETCH s.zone z " +
           "JOIN FETCH z.floor f JOIN FETCH f.building " +
           "JOIN FETCH r.vehicle JOIN FETCH r.user " +
           "WHERE r.reservationCode = :reservationCode")
    Optional<Reservation> findByReservationCodeFetchingDetails(@Param("reservationCode") String reservationCode);

    int countByUserUserId(String userId);

    Optional<Reservation> findFirstByUserUserIdOrderByCreatedAtAsc(String userId);

    // FIFO: xe đặt trước checkin trước - lấy PENDING reservations theo thứ tự createdAt ASC
    @Query(value = "SELECT r FROM Reservation r JOIN r.slot s JOIN s.zone z JOIN z.floor f " +
           "WHERE f.building.buildingId = :buildingId AND r.reservationStatus = 'PENDING' ORDER BY r.createdAt ASC")
    @EntityGraph(attributePaths = {"slot", "slot.zone", "slot.zone.floor", "slot.zone.floor.building", "vehicle", "user"})
    List<Reservation> findPendingByBuildingOrderByCreatedAtAsc(@Param("buildingId") String buildingId);

    @Query("SELECT r FROM Reservation r "
           + "JOIN FETCH r.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building "
           + "JOIN FETCH r.vehicle v JOIN FETCH v.vehicleType "
           + "JOIN FETCH r.user "
           + "WHERE v.vehicleId = :vehicleId "
           + "AND r.reservationStatus IN ('PENDING', 'APPROVED') "
           + "ORDER BY r.createdAt DESC")
    List<Reservation> findPendingByVehicleId(@Param("vehicleId") String vehicleId, Pageable pageable);

    default Optional<Reservation> findFirstPendingByVehicleId(String vehicleId) {
        return findPendingByVehicleId(vehicleId, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    /**
     * Tìm reservation PENDING/APPROVED theo biển số xe (case-insensitive).
     * Dùng trong quick checkin: staff quét biển số → hệ thống tự tìm reservation phù hợp.
     */
    @Query("SELECT r FROM Reservation r " +
           "JOIN FETCH r.slot s JOIN FETCH s.zone z JOIN FETCH z.floor f JOIN FETCH f.building " +
           "JOIN FETCH r.vehicle v JOIN FETCH v.vehicleType " +
           "JOIN FETCH r.user " +
           "WHERE UPPER(v.plateNumber) = UPPER(:plateNumber) " +
           "AND r.reservationStatus IN ('PENDING', 'APPROVED') " +
           "ORDER BY r.createdAt DESC")
    List<Reservation> findPendingByPlateNumber(@Param("plateNumber") String plateNumber);

    /**
     * Tìm tất cả reservation ACTIVE (chưa COMPLETED/CANCELLED/EXPIRED) theo biển số.
     * Dùng để ngăn chặn guest checkin khi driver đã checkin rồi (reservation status = CHECKED_IN).
     * 
     * NOTE: Sử dụng findPendingByPlateNumber và lọc thêm CHECKED_IN trong service
     * vì JPA không hỗ trợ đầy đủ path traversal trên tất cả các query patterns.
     */
    List<Reservation> findByVehiclePlateNumberIgnoreCase(String plateNumber);

    List<Reservation> findByVehicleVehicleId(String vehicleId);

    // Check active reservations by vehicle type (không dùng time range nữa)
    @Query("SELECT r FROM Reservation r JOIN r.vehicle v JOIN v.vehicleType vt " +
           "WHERE r.user.userId = :userId " +
           "AND vt.typeName = :vehicleTypeName " +
           "AND r.reservationStatus IN :statuses")
    List<Reservation> findActiveReservationsByVehicleType(
            @Param("userId") String userId,
            @Param("vehicleTypeName") String vehicleTypeName,
            @Param("statuses") Collection<String> statuses);

    // ============ DASHBOARD STATS ============

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.reservationStatus = :status")
    long countByReservationStatus(@Param("status") String status);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.createdAt >= :from AND r.createdAt < :to")
    long countReservationsInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
