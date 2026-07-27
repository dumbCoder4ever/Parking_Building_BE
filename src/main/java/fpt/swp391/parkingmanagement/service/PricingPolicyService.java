package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.PricingPolicyRequest;
import fpt.swp391.parkingmanagement.dto.PricingPolicyResponse;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.DuplicateResourceException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PricingPolicyService {

    private final PricingPolicyRepository pricingPolicyRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    public PricingPolicyResponse createPricingPolicy(PricingPolicyRequest request) {
        if (pricingPolicyRepository.findByPolicyName(request.getPolicyName()).isPresent()) {
            throw new DuplicateResourceException("Pricing policy with name '" + request.getPolicyName() + "' already exists");
        }

        PricingPolicy policy = toEntity(request);
        PricingPolicy saved = pricingPolicyRepository.save(policy);
        return toResponseDTO(saved);
    }

    public PricingPolicyResponse updatePricingPolicy(String id, PricingPolicyRequest request) {
        PricingPolicy policy = pricingPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing policy not found with id: " + id));

        if (!policy.getPolicyName().equals(request.getPolicyName())
                && pricingPolicyRepository.findByPolicyName(request.getPolicyName()).isPresent()) {
            throw new DuplicateResourceException("Pricing policy with name '" + request.getPolicyName() + "' already exists");
        }

        updateFields(policy, request);
        return toResponseDTO(pricingPolicyRepository.save(policy));
    }

    public void deletePricingPolicy(String id) {
        if (!pricingPolicyRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pricing policy not found with id: " + id);
        }
        pricingPolicyRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PricingPolicyResponse> getAllPricingPolicies() {
        return pricingPolicyRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PricingPolicyResponse> findActiveByVehicleTypeId(String vehicleTypeId) {
        return pricingPolicyRepository.findAllActiveForVehicleType(vehicleTypeId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PricingPolicyResponse> getActiveSummary() {
        return pricingPolicyRepository.findAll().stream()
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public PricingPolicyResponse getPricingPolicyById(String id) {
        PricingPolicy policy = pricingPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing policy not found with id: " + id));
        return toResponseDTO(policy);
    }

    private PricingPolicy toEntity(PricingPolicyRequest request) {
        PricingPolicy policy = new PricingPolicy();
        policy.setPolicyName(request.getPolicyName());
        policy.setBasePrice(request.getBasePrice());
        policy.setHourlyRate(request.getHourlyRate());
        policy.setMaxHours(request.getMaxHours() != null ? request.getMaxHours() : 24);
        policy.setEffectiveFrom(request.getEffectiveFrom());
        policy.setEffectiveTo(request.getEffectiveTo());
        policy.setVehicleType(resolveVehicleType(request.getVehicleTypeId()));
        if (request.getStatus() != null) {
            policy.setStatus(request.getStatus());
        }
        policy.setPricingType(request.getPricingType() != null ? request.getPricingType() : "STANDARD");
        return policy;
    }

    private void updateFields(PricingPolicy policy, PricingPolicyRequest request) {
        policy.setPolicyName(request.getPolicyName());
        policy.setBasePrice(request.getBasePrice());
        policy.setHourlyRate(request.getHourlyRate());
        policy.setMaxHours(request.getMaxHours() != null ? request.getMaxHours() : 24);
        policy.setEffectiveFrom(request.getEffectiveFrom());
        policy.setEffectiveTo(request.getEffectiveTo());
        policy.setVehicleType(resolveVehicleType(request.getVehicleTypeId()));
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            policy.setStatus(request.getStatus().trim().toUpperCase());
        }
        if (request.getPricingType() != null && !request.getPricingType().isBlank()) {
            policy.setPricingType(request.getPricingType());
        }
    }

    private PricingPolicyResponse toResponseDTO(PricingPolicy policy) {
        PricingPolicyResponse dto = new PricingPolicyResponse();
        dto.setPolicyId(policy.getPolicyId());
        dto.setPolicyName(policy.getPolicyName());
        dto.setBasePrice(policy.getBasePrice());
        dto.setHourlyRate(policy.getHourlyRate());
        dto.setMaxHours(policy.getMaxHours());
        dto.setEffectiveFrom(policy.getEffectiveFrom());
        dto.setEffectiveTo(policy.getEffectiveTo());
        dto.setStatus(policy.getStatus());
        dto.setCreatedAt(policy.getCreatedAt());
        dto.setVehicleTypeId(policy.getVehicleType().getVehicleTypeId());
        dto.setTypeName(policy.getVehicleType().getTypeName());
        dto.setPricingType(policy.getPricingType());
        return dto;
    }

    private VehicleType resolveVehicleType(String vehicleTypeId) {
        if (vehicleTypeId == null || vehicleTypeId.isBlank()) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST, "Vehicle type ID cannot be null or empty");
        }
        return vehicleTypeRepository.findById(vehicleTypeId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                        "Vehicle type not found with id: " + vehicleTypeId));
    }
}
