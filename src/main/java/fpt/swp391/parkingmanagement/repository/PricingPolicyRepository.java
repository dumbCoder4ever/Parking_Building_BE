package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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
            + "and (p.effectiveFrom is null or p.effectiveFrom <= :now) "
            + "and (p.effectiveTo is null or p.effectiveTo >= :now) "
            + "order by p.createdAt desc")
    List<PricingPolicy> findAllActiveForVehicleTypeAt(
            @Param("vehicleTypeId") String vehicleTypeId, @Param("now") LocalDateTime now);

    default List<PricingPolicy> findAllActiveForVehicleType(String vehicleTypeId) {
        return findAllActiveForVehicleTypeAt(vehicleTypeId, LocalDateTime.now());
    }

    @EntityGraph(attributePaths = {"vehicleType"})
    @Query("select p from PricingPolicy p where p.vehicleType.vehicleTypeId in :vehicleTypeIds "
            + "and p.status = 'ACTIVE' "
            + "and (p.effectiveFrom is null or p.effectiveFrom <= :now) "
            + "and (p.effectiveTo is null or p.effectiveTo >= :now) "
            + "order by p.createdAt desc")
    List<PricingPolicy> findAllActiveForVehicleTypesAt(
            @Param("vehicleTypeIds") Collection<String> vehicleTypeIds, @Param("now") LocalDateTime now);

    default List<PricingPolicy> findAllActiveForVehicleTypes(Collection<String> vehicleTypeIds) {
        return findAllActiveForVehicleTypesAt(vehicleTypeIds, LocalDateTime.now());
    }

    @EntityGraph(attributePaths = {"vehicleType"})
    @Query("select p from PricingPolicy p where p.vehicleType.vehicleTypeId = :vehicleTypeId "
            + "and p.status = 'ACTIVE' "
            + "and (p.effectiveFrom is null or p.effectiveFrom <= :now) "
            + "and (p.effectiveTo is null or p.effectiveTo >= :now) "
            + "order by p.createdAt desc limit 1")
    Optional<PricingPolicy> findActiveForVehicleTypeAt(
            @Param("vehicleTypeId") String vehicleTypeId, @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = {"vehicleType"})
    @Query("select p from PricingPolicy p where p.vehicleType.vehicleTypeId = :vehicleTypeId "
            + "and p.status = 'ACTIVE' "
            + "order by p.createdAt desc limit 1")
    Optional<PricingPolicy> findActiveStatusOnlyForVehicleType(@Param("vehicleTypeId") String vehicleTypeId);

    default Optional<PricingPolicy> findActiveForVehicleType(String vehicleTypeId) {
        LocalDateTime now = LocalDateTime.now();
        return findActiveForVehicleTypeAt(vehicleTypeId, now)
                .or(() -> findActiveStatusOnlyForVehicleType(vehicleTypeId));
    }

    @EntityGraph(attributePaths = {"vehicleType"})
    @Query("select p from PricingPolicy p "
            + "join p.vehicleType vt where lower(vt.typeName) = lower(:typeName) "
            + "and p.status = 'ACTIVE' "
            + "and (p.effectiveFrom is null or p.effectiveFrom <= :now) "
            + "and (p.effectiveTo is null or p.effectiveTo >= :now) "
            + "order by p.createdAt desc limit 1")
    Optional<PricingPolicy> findActiveForVehicleTypeNameAt(
            @Param("typeName") String typeName, @Param("now") LocalDateTime now);

    @Query("select p.vehicleType from PricingPolicy p where p.vehicleType.vehicleTypeId = :vehicleTypeId")
    VehicleType findVehicleTypeByVehicleTypeId(@Param("vehicleTypeId") String vehicleTypeId);

    @Query("select p from PricingPolicy p where p.status = 'ACTIVE' "
            + "and p.effectiveTo is not null and p.effectiveTo < :now")
    List<PricingPolicy> findExpiredActivePoliciesAt(@Param("now") LocalDateTime now);

    default List<PricingPolicy> findExpiredActivePolicies() {
        return findExpiredActivePoliciesAt(LocalDateTime.now());
    }

    @Query("select p from PricingPolicy p where p.vehicleType.vehicleTypeId in :vehicleTypeIds "
            + "and p.status = 'ACTIVE' "
            + "and (p.effectiveFrom is null or p.effectiveFrom <= :now) "
            + "and (p.effectiveTo is null or p.effectiveTo >= :now) "
            + "order by p.createdAt desc")
    List<PricingPolicy> findAllActiveByVehicleTypeIdsAt(
            @Param("vehicleTypeIds") Collection<String> vehicleTypeIds, @Param("now") LocalDateTime now);

    default List<PricingPolicy> findAllActiveByVehicleTypeIds(Collection<String> vehicleTypeIds) {
        return findAllActiveByVehicleTypeIdsAt(vehicleTypeIds, LocalDateTime.now());
    }

    @Modifying
    @Transactional
    @Query("update PricingPolicy p set p.status = 'INACTIVE' "
            + "where p.status = 'ACTIVE' "
            + "and p.effectiveTo is not null "
            + "and p.effectiveTo < :now")
    int updateExpiredPoliciesAt(@Param("now") LocalDateTime now);

    default int updateExpiredPolicies() {
        return updateExpiredPoliciesAt(LocalDateTime.now());
    }
}
