package fpt.swp391.parkingmanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.VehicleType;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, String> {

    boolean existsByVehicleTypeVehicleTypeId(String vehicleTypeId);

    Optional<PricingPolicy> findByPolicyName(String policyName);

    @EntityGraph(attributePaths = {"vehicleType"})
    List<PricingPolicy> findAll();

    @EntityGraph(attributePaths = {"vehicleType"})
    List<PricingPolicy> findByVehicleTypeVehicleTypeId(String vehicleTypeId);

    @EntityGraph(attributePaths = {"vehicleType"})
    @Query("select p from PricingPolicy p where p.vehicleType.vehicleTypeId = :vehicleTypeId "
            + "and p.status = 'ACTIVE' "
            + "and (p.effectiveFrom is null or p.effectiveFrom <= current_timestamp) "
            + "and (p.effectiveTo is null or p.effectiveTo >= current_timestamp) "
            + "order by p.createdAt desc")
    List<PricingPolicy> findAllActiveForVehicleType(@Param("vehicleTypeId") String vehicleTypeId);

    @EntityGraph(attributePaths = {"vehicleType"})
    @Query("select p from PricingPolicy p where p.vehicleType.vehicleTypeId in :vehicleTypeIds "
            + "and p.status = 'ACTIVE' "
            + "and (p.effectiveFrom is null or p.effectiveFrom <= current_timestamp) "
            + "and (p.effectiveTo is null or p.effectiveTo >= current_timestamp) "
            + "order by p.createdAt desc")
    List<PricingPolicy> findAllActiveForVehicleTypes(@Param("vehicleTypeIds") Collection<String> vehicleTypeIds);

    @EntityGraph(attributePaths = {"vehicleType"})
    @Query("select p from PricingPolicy p where p.vehicleType.vehicleTypeId = :vehicleTypeId "
            + "and p.status = 'ACTIVE' "
            + "and (p.effectiveFrom is null or p.effectiveFrom <= current_timestamp) "
            + "and (p.effectiveTo is null or p.effectiveTo >= current_timestamp) "
            + "order by p.createdAt desc limit 1")
    Optional<PricingPolicy> findActiveForVehicleType(@Param("vehicleTypeId") String vehicleTypeId);

    @Query("select p.vehicleType from PricingPolicy p where p.vehicleType.vehicleTypeId = :vehicleTypeId")
    VehicleType findVehicleTypeByVehicleTypeId(@Param("vehicleTypeId") String vehicleTypeId);

    @Query("select p from PricingPolicy p where p.status = 'ACTIVE' and p.effectiveTo is not null and p.effectiveTo < current_timestamp")
    List<PricingPolicy> findExpiredActivePolicies();

    /**
     * Batch version: load active pricing policies for multiple vehicle types in ONE query.
     * Used by ReservationService.buildBuildingEnrichment to replace N x findAllActiveForVehicleType.
     */
    @Query("select p from PricingPolicy p where p.vehicleType.vehicleTypeId in :vehicleTypeIds "
            + "and p.status = 'ACTIVE' "
            + "and (p.effectiveFrom is null or p.effectiveFrom <= current_timestamp) "
            + "and (p.effectiveTo is null or p.effectiveTo >= current_timestamp) "
            + "order by p.createdAt desc")
    List<PricingPolicy> findAllActiveByVehicleTypeIds(@Param("vehicleTypeIds") java.util.Collection<String> vehicleTypeIds);

    @Modifying
    @Query("update PricingPolicy p set p.status = 'INACTIVE' "
            + "where p.status = 'ACTIVE' "
            + "and p.effectiveTo is not null "
            + "and p.effectiveTo < current_timestamp")
    int updateExpiredPolicies();
}
