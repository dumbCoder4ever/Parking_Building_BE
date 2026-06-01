package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.PricingPolicy;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, String> {
    List<PricingPolicy> findByVehicleTypeVehicleTypeId(String vehicleTypeId);
}
