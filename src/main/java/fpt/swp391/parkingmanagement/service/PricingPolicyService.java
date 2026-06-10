package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.PricingPolicyRequest;
import fpt.swp391.parkingmanagement.dto.PricingPolicyResponse;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PricingPolicyService {
    @Autowired
    private PricingPolicyRepository pricingPolicyRepository;

    @Autowired
    private Validator validator;

    // Tạo policy mới
    public PricingPolicyResponse createPricingPolicy(PricingPolicyRequest pricingPolicyRequest) {
        // exist event
        Optional<PricingPolicy> existingPolicy = pricingPolicyRepository.findByPolicyName(pricingPolicyRequest.getPolicyName());
        if (existingPolicy.isPresent()) {
            throw new IllegalArgumentException("Pricing policy with name '" + pricingPolicyRequest.getPolicyName() + "' already exists");
        }

        PricingPolicy pricingPolicy = convertToEntity(pricingPolicyRequest);

        Set<ConstraintViolation<PricingPolicy>> violations = validator.validate(pricingPolicy);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    violations.stream()
                            .map(v -> v.getMessage())
                            .collect(Collectors.joining(", "))
            );
        }

        PricingPolicy savedEvent = pricingPolicyRepository.save(pricingPolicy);
        return convertToResponseDTO(savedEvent);
    }

    // Cập nhật pricing policy
    public PricingPolicyResponse updatePricingPolicy(String id, PricingPolicyRequest pricingPolicyRequest) {
        Optional<PricingPolicy> existingPolicy = pricingPolicyRepository.findById(id);
        if (existingPolicy.isEmpty()) {
            throw new IllegalArgumentException("Pricing policy not found with id: " + id);
        }

        Set<ConstraintViolation<Optional<PricingPolicy>>> violations = validator.validate(existingPolicy);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    violations.stream()
                            .map(v -> v.getMessage())
                            .collect(Collectors.joining(", "))
            );
        }

        PricingPolicy pricingPolicy = existingPolicy.get();
        updatePricingPolicyFields(pricingPolicy, pricingPolicyRequest);

        PricingPolicy updatedEvent = pricingPolicyRepository.save(pricingPolicy);
        return convertToResponseDTO(updatedEvent);
    }

    // Xóa pricing policy
    public void deletePricingPolicy(String id) {
        Optional<PricingPolicy> policy = pricingPolicyRepository.findById(id);
        if (policy.isEmpty()) {
            throw new IllegalArgumentException("Event not found with id: " + id);
        }
        Set<ConstraintViolation<Optional<PricingPolicy>>> violations = validator.validate(policy);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    violations.stream()
                            .map(v -> v.getMessage())
                            .collect(Collectors.joining(", "))
            );
        }
        pricingPolicyRepository.deleteById(id);
    }

    // Lấy tất cả pricing policies
    public List<PricingPolicyResponse> getAllPricingPolicies() {
        List<PricingPolicy> policies = pricingPolicyRepository.findAll();
        return policies.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // Lấy active pricing policies từ vehicle type ID
    public List<PricingPolicyResponse> findActiveStatusByVehicleTypeId(String vehicleTypeId) {
        List<PricingPolicy> policies = pricingPolicyRepository.findAllActiveForVehicleType(vehicleTypeId);
        return policies.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // Lấy pricing policy theo ID
    public PricingPolicyResponse getPricingPolicyById(String id) {
        PricingPolicy policy = pricingPolicyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found with id : "+id));
        Set<ConstraintViolation<PricingPolicy>> violations = validator.validate(policy);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    violations.stream()
                            .map(v -> v.getMessage())
                            .collect(Collectors.joining(", "))
            );
        }
        return this.convertToResponseDTO(policy);
    }

    // Chuyển đổi Entity sang ResponseDTO
    private PricingPolicyResponse convertToResponseDTO(PricingPolicy pricingPolicy) {
        PricingPolicyResponse responseDTO = new PricingPolicyResponse();

        responseDTO.setPolicyId(pricingPolicy.getPolicyId());
        responseDTO.setPolicyName(pricingPolicy.getPolicyName());
        responseDTO.setPricingType(pricingPolicy.getPricingType());
        responseDTO.setStatus(pricingPolicy.getStatus());
        responseDTO.setBasePrice(pricingPolicy.getBasePrice());
        responseDTO.setCreatedAt(pricingPolicy.getCreatedAt());
        responseDTO.setEffectiveFrom(pricingPolicy.getEffectiveFrom());
        responseDTO.setEffectiveTo(pricingPolicy.getEffectiveTo());
        responseDTO.setHourlyRate(pricingPolicy.getHourlyRate());
        responseDTO.setLostTicketFee(pricingPolicy.getLostTicketFee());
        responseDTO.setMaxDailyFee(pricingPolicy.getMaxDailyFee());
        responseDTO.setOvernightFee(pricingPolicy.getOvernightFee());
        responseDTO.setPeakHourMultiplier(pricingPolicy.getPeakHourMultiplier());
        responseDTO.setVehicleTypeId(pricingPolicy.getVehicleType().getVehicleTypeId());
        //vehicle
        responseDTO.setTypeName(pricingPolicy.getVehicleType().getTypeName());
        responseDTO.setSizeCategory(pricingPolicy.getVehicleType().getSizeCategory());
        responseDTO.setDescription(pricingPolicy.getVehicleType().getDescription());
        return responseDTO;
    }

    //Chuyển đổi RequestDTO sang Entity
    private PricingPolicy convertToEntity(PricingPolicyRequest requestDTO) {
        PricingPolicy policy = new PricingPolicy();
        policy.setPolicyName(requestDTO.getPolicyName());
        policy.setPricingType(requestDTO.getPricingType());
        //policy.setStatus(requestDTO.getStatus());
        policy.setBasePrice(requestDTO.getBasePrice());
        policy.setEffectiveFrom(requestDTO.getEffectiveFrom());
        policy.setEffectiveTo(requestDTO.getEffectiveTo());
        policy.setHourlyRate(requestDTO.getHourlyRate());
        policy.setLostTicketFee(requestDTO.getLostTicketFee());
        policy.setMaxDailyFee(requestDTO.getMaxDailyFee());
        policy.setOvernightFee(requestDTO.getOvernightFee());
        policy.setPeakHourMultiplier(requestDTO.getPeakHourMultiplier());
        VehicleType vehicleType = pricingPolicyRepository.findVehicleTypeByVehicleTypeId(requestDTO.getVehicleTypeId());
        policy.setVehicleType(vehicleType);
        return policy;
    }

    //Update field
    private void updatePricingPolicyFields(PricingPolicy policy, PricingPolicyRequest requestDTO) {
        policy.setPolicyName(requestDTO.getPolicyName());
        policy.setPricingType(requestDTO.getPricingType());
        //policy.setStatus(requestDTO.getStatus());
        policy.setBasePrice(requestDTO.getBasePrice());
        policy.setEffectiveFrom(requestDTO.getEffectiveFrom());
        policy.setEffectiveTo(requestDTO.getEffectiveTo());
        policy.setHourlyRate(requestDTO.getHourlyRate());
        policy.setLostTicketFee(requestDTO.getLostTicketFee());
        policy.setMaxDailyFee(requestDTO.getMaxDailyFee());
        policy.setOvernightFee(requestDTO.getOvernightFee());
        policy.setPeakHourMultiplier(requestDTO.getPeakHourMultiplier());
        //policy.setVehicleType(requestDTO.getVehicleType());
        VehicleType vehicleType = pricingPolicyRepository.findVehicleTypeByVehicleTypeId(requestDTO.getVehicleTypeId());
        policy.setVehicleType(vehicleType);
    }

    //Auto status INACTIVE after passes effective_to
    @Scheduled(fixedRate = 60000)
    public void updateExpiredPricingPolicies() {
        try {
            int updatedCount = pricingPolicyRepository.updateExpiredPolicies();

            if (updatedCount > 0) {
                log.info("Successfully deactivated {} expired pricing policies", updatedCount);

                // Log details of expired policies
                List<PricingPolicy> expiredPolicies = pricingPolicyRepository.findExpiredActivePolicies();
                expiredPolicies.forEach(policy ->
                        log.debug("Deactivated pricing policy '{}' (ID: {}). Effective until: {}",
                                policy.getPolicyName(), policy.getPolicyId(), policy.getEffectiveTo())
                );
            }
        } catch (Exception e) {
            log.error("Error updating expired pricing policies", e);
        }
    }
}
