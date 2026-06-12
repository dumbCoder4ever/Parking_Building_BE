package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.PricingPolicyRequest;
import fpt.swp391.parkingmanagement.dto.PricingPolicyResponse;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import fpt.swp391.parkingmanagement.service.PricingPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager/pricing-policy")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class PricingPolicyController {

    @Autowired
    private PricingPolicyService pricingPolicyService;

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @Operation(summary = "Create pricing policy")
    @PostMapping
    public ResponseEntity<?> createPricingPolicy(@Valid @RequestBody PricingPolicyRequest pricingPolicyRequest) {
        try {
            if(pricingPolicyRequest.getPolicyName() == null || pricingPolicyRequest.getPolicyName().isEmpty())
            {
                return ResponseEntity.badRequest().body("Policy name is required");
            }
            if(pricingPolicyRequest.getVehicleTypeId() == null || pricingPolicyRequest.getVehicleTypeId().isEmpty()) {
                return ResponseEntity.badRequest().body("Correct vehicle type ID is required");
            }
            PricingPolicyResponse createdPricingPolicy = pricingPolicyService.createPricingPolicy(pricingPolicyRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPricingPolicy);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal server error: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    @Operation(summary = "Get all pricing policy")
    @GetMapping
    public ResponseEntity<List<PricingPolicyResponse>> getAllPricingPolicies() {
        try {
            List<PricingPolicyResponse> policies = pricingPolicyService.getAllPricingPolicies();
            return ResponseEntity.ok(policies);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get pricing policy by id")
    @GetMapping("/{id}")
    public ResponseEntity<?> getPricingPolicyById(@PathVariable String id) {
        try {
            PricingPolicyResponse policy = pricingPolicyService.getPricingPolicyById(id);
            return ResponseEntity.ok(policy);
        } catch (RuntimeException e) {
            //return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get all active pricing policies by vehicle type id")
    @GetMapping("/active/{id}")
    public ResponseEntity<List<PricingPolicyResponse>> findActiveStatusByVehicleTypeId(@PathVariable String id) {
        try {
            List<PricingPolicyResponse> policies = pricingPolicyService.findActiveStatusByVehicleTypeId(id);
            return ResponseEntity.ok(policies);
        }  catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update pricing policy")
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePricingPolicy(@PathVariable String id,
                                         @Valid @RequestBody PricingPolicyRequest pricingPolicyRequest) {
        try {
            PricingPolicyResponse updatedPolicy = pricingPolicyService.updatePricingPolicy(id, pricingPolicyRequest);
            return ResponseEntity.ok(updatedPolicy);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete pricing policy")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePricingPolicy(@PathVariable String id) {
        try {
            pricingPolicyService.deletePricingPolicy(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
