package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {

    Optional<Vehicle> findByPlateNumberIgnoreCase(String plateNumber);

    @Query("""
            SELECT v FROM Vehicle v
            WHERE REPLACE(REPLACE(REPLACE(UPPER(v.plateNumber), '-', ''), ' ', ''), '.', '') = :normalizedPlate
            """)
    Optional<Vehicle> findByNormalizedPlateNumber(@Param("normalizedPlate") String normalizedPlate);

    @Query("""
            SELECT v FROM Vehicle v
            WHERE REPLACE(REPLACE(REPLACE(UPPER(v.plateNumber), '-', ''), ' ', ''), '.', '')
                  LIKE CONCAT(:prefix, '%')
            """)
    List<Vehicle> findByNormalizedPlateStartingWith(@Param("prefix") String prefix, Pageable pageable);

    Optional<Vehicle> findByPlateNumber(String plateNumber);

    Optional<Vehicle> findByVehicleIdAndUserUserId(String vehicleId, String userId);

    @EntityGraph(attributePaths = {"user", "vehicleType"})
    List<Vehicle> findByPlateNumberContainingIgnoreCaseOrderByCreatedAtDesc(String plateNumber);

    @EntityGraph(attributePaths = {"user", "vehicleType"})
    List<Vehicle> findAllByOrderByCreatedAtDesc();

    boolean existsByPlateNumberIgnoreCase(String plateNumber);

    boolean existsByPlateNumberIgnoreCaseAndVehicleIdNot(String plateNumber, String vehicleId);

    boolean existsByVehicleTypeVehicleTypeId(String vehicleTypeId);

    List<Vehicle> findByUserUserId(String userId);

    /**
     * FIX N+1: Load kèm vehicleType + user trong 1 query (LEFT JOIN).
     * Trước đây chỉ fetch Vehicle, mỗi vehicle trigger 1-2 query thêm
     * khi VehicleResponse đọc vehicleType/user → N+1.
     */
    @EntityGraph(attributePaths = {"vehicleType", "user"})
    List<Vehicle> findByUserUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByPlateNumber(String plateNumber);

    int countByUserUserId(String userId);

    @Query("""
            SELECT v FROM Vehicle v
            JOIN FETCH v.user u
            JOIN FETCH v.vehicleType vt
            WHERE (:plateNumber IS NULL OR LOWER(v.plateNumber) LIKE LOWER(CONCAT('%', :plateNumber, '%')))
            AND (:status IS NULL OR UPPER(v.status) = UPPER(:status))
            AND (:userId IS NULL OR u.userId = :userId)
            AND (:username IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')))
            AND (:ownerFullName IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :ownerFullName, '%')))
            AND (:vehicleTypeId IS NULL OR vt.vehicleTypeId = :vehicleTypeId)
            AND (
                :parked IS NULL
                OR (:parked = TRUE AND EXISTS (
                    SELECT 1 FROM ParkingSession psa
                    WHERE psa.vehicle = v AND UPPER(psa.sessionStatus) = 'ACTIVE'
                ))
                OR (:parked = FALSE AND NOT EXISTS (
                    SELECT 1 FROM ParkingSession psb
                    WHERE psb.vehicle = v AND UPPER(psb.sessionStatus) = 'ACTIVE'
                ))
            )
            AND (
                (:checkInFrom IS NULL AND :checkInTo IS NULL)
                OR EXISTS (
                    SELECT 1 FROM ParkingSession psc
                    WHERE psc.vehicle = v
                    AND psc.checkinTime = (
                        SELECT MAX(psd.checkinTime) FROM ParkingSession psd WHERE psd.vehicle = v
                    )
                    AND (:checkInFrom IS NULL OR psc.checkinTime >= :checkInFrom)
                    AND (:checkInTo IS NULL OR psc.checkinTime <= :checkInTo)
                )
            )
            ORDER BY v.createdAt DESC
            """)
    List<Vehicle> searchForManager(
            @Param("plateNumber") String plateNumber,
            @Param("status") String status,
            @Param("userId") String userId,
            @Param("username") String username,
            @Param("ownerFullName") String ownerFullName,
            @Param("vehicleTypeId") String vehicleTypeId,
            @Param("parked") Boolean parked,
            @Param("checkInFrom") LocalDateTime checkInFrom,
            @Param("checkInTo") LocalDateTime checkInTo);
}
