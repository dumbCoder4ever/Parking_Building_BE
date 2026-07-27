package fpt.swp391.parkingmanagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.VehicleType;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, String> {
    Optional<VehicleType> findByTypeName(String typeName);

    Optional<VehicleType> findByTypeNameIgnoreCase(String typeName);

    Optional<VehicleType> findByVehicleTypeId(String vehicleTypeId);

    boolean existsByTypeNameIgnoreCase(String typeName);

    boolean existsByTypeNameIgnoreCaseAndVehicleTypeIdNot(String typeName, String vehicleTypeId);

    @Modifying
    @Query("UPDATE Floor f SET f.vehicleType.vehicleTypeId = :newId WHERE f.vehicleType.vehicleTypeId = :oldId")
    int updateFloorVehicleTypeId(@Param("oldId") String oldId, @Param("newId") String newId);

    @Modifying
    @Query("UPDATE Vehicle v SET v.vehicleType.vehicleTypeId = :newId WHERE v.vehicleType.vehicleTypeId = :oldId")
    int updateVehicleVehicleTypeId(@Param("oldId") String oldId, @Param("newId") String newId);

    @Modifying
    @Query("UPDATE PricingPolicy p SET p.vehicleType.vehicleTypeId = :newId WHERE p.vehicleType.vehicleTypeId = :oldId")
    int updatePricingPolicyVehicleTypeId(@Param("oldId") String oldId, @Param("newId") String newId);
}
