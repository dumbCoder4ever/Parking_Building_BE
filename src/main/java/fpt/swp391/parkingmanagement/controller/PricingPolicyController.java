package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.PricingPolicyRequest;
import fpt.swp391.parkingmanagement.dto.PricingPolicyResponse;
import fpt.swp391.parkingmanagement.service.PricingPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manager/pricing-policy")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class PricingPolicyController {

    private final PricingPolicyService pricingPolicyService;

    @Operation(summary = "Create pricing policy")
    @PostMapping
    public ResponseEntity<ApiResponse<PricingPolicyResponse>> createPricingPolicy(
            @Valid @RequestBody PricingPolicyRequest request) {
        PricingPolicyResponse created = pricingPolicyService.createPricingPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pricing policy created successfully", created));
    }

    @Operation(summary = "Get all pricing policies")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PricingPolicyResponse>>> getAllPricingPolicies() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Pricing policies retrieved successfully",
                pricingPolicyService.getAllPricingPolicies()));
    }

    @Operation(summary = "Get pricing policy by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PricingPolicyResponse>> getPricingPolicyById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Pricing policy retrieved successfully",
                pricingPolicyService.getPricingPolicyById(id)));
    }

    @Operation(summary = "Get all active pricing policies by vehicle type id")
    @GetMapping("/active/{vehicleTypeId}")
    public ResponseEntity<ApiResponse<List<PricingPolicyResponse>>> findActiveByVehicleTypeId(
            @PathVariable String vehicleTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Active pricing policies retrieved successfully",
                pricingPolicyService.findActiveByVehicleTypeId(vehicleTypeId)));
    }

    @Operation(summary = "Update pricing policy")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PricingPolicyResponse>> updatePricingPolicy(
            @PathVariable String id,
            @Valid @RequestBody PricingPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Pricing policy updated successfully",
                pricingPolicyService.updatePricingPolicy(id, request)));
    }

    @Operation(summary = "Get pricing summary for all vehicle types")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<List<PricingPolicyResponse>>> getPricingSummary() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Pricing summary retrieved successfully",
                pricingPolicyService.getActiveSummary()));
    }

    @Operation(summary = "Delete pricing policy")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePricingPolicy(@PathVariable String id) {
        pricingPolicyService.deletePricingPolicy(id);
        return ResponseEntity.ok(ApiResponse.ok("Pricing policy deleted successfully", null));
    }
}
