package fpt.swp391.parkingmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.PricingPolicy;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, String> {
    List<PricingPolicy> findByVehicleTypeVehicleTypeId(String vehicleTypeId);

    boolean existsByVehicleTypeVehicleTypeId(String vehicleTypeId);

    @Query("select p from PricingPolicy p where p.vehicleType.vehicleTypeId = :vehicleTypeId "
            + "and p.status = 'ACTIVE' "
            + "and (p.effectiveFrom is null or p.effectiveFrom <= CURRENT_TIMESTAMP) "
            + "and (p.effectiveTo is null or p.effectiveTo >= CURRENT_TIMESTAMP) "
            + "order by p.createdAt desc")
    Optional<PricingPolicy> findActiveForVehicleType(@Param("vehicleTypeId") String vehicleTypeId);
}
