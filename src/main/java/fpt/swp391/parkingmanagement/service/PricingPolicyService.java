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
        // Tier fields
        policy.setTier1Hours(request.getTier1Hours());
        policy.setTier1Price(request.getTier1Price());
        policy.setTier2Hours(request.getTier2Hours());
        policy.setTier2Price(request.getTier2Price());
        policy.setTier3Hours(request.getTier3Hours());
        policy.setTier3Price(request.getTier3Price());
        policy.setTier4Hours(request.getTier4Hours());
        policy.setTier4Price(request.getTier4Price());
        policy.setPerDayPrice(request.getPerDayPrice());
        policy.setOvernightFee(request.getOvernightFee());
        policy.setLostTicketFee(request.getLostTicketFee());
        policy.setPeakHourMultiplier(request.getPeakHourMultiplier());
        policy.setMaxDailyFee(request.getMaxDailyFee());
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
        // Tier fields
        policy.setTier1Hours(request.getTier1Hours());
        policy.setTier1Price(request.getTier1Price());
        policy.setTier2Hours(request.getTier2Hours());
        policy.setTier2Price(request.getTier2Price());
        policy.setTier3Hours(request.getTier3Hours());
        policy.setTier3Price(request.getTier3Price());
        policy.setTier4Hours(request.getTier4Hours());
        policy.setTier4Price(request.getTier4Price());
        policy.setPerDayPrice(request.getPerDayPrice());
        policy.setOvernightFee(request.getOvernightFee());
        policy.setLostTicketFee(request.getLostTicketFee());
        policy.setPeakHourMultiplier(request.getPeakHourMultiplier());
        policy.setMaxDailyFee(request.getMaxDailyFee());
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
        dto.setTier1Hours(policy.getTier1Hours());
        dto.setTier1Price(policy.getTier1Price());
        dto.setTier2Hours(policy.getTier2Hours());
        dto.setTier2Price(policy.getTier2Price());
        dto.setTier3Hours(policy.getTier3Hours());
        dto.setTier3Price(policy.getTier3Price());
        dto.setTier4Hours(policy.getTier4Hours());
        dto.setTier4Price(policy.getTier4Price());
        dto.setPerDayPrice(policy.getPerDayPrice());
        dto.setOvernightFee(policy.getOvernightFee());
        dto.setLostTicketFee(policy.getLostTicketFee());
        dto.setPeakHourMultiplier(policy.getPeakHourMultiplier());
        dto.setMaxDailyFee(policy.getMaxDailyFee());
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
