package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.BuildingRuleResponse;
import fpt.swp391.parkingmanagement.dto.CreateBuildingRuleRequest;
import fpt.swp391.parkingmanagement.dto.UpdateBuildingRuleRequest;
import fpt.swp391.parkingmanagement.service.BuildingRuleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/manager/buildings/{buildingId}/rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class BuildingRuleController {

    private final BuildingRuleService buildingRuleService;

    @Operation(summary = "List building rules")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BuildingRuleResponse>>> list(@PathVariable String buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building rules retrieved successfully",
                buildingRuleService.listByBuilding(buildingId)));
    }

    @Operation(
            summary = "Create building rule",
            description = "Allowed ruleCode: NO_OVERNIGHT, MAX_PARKING_HOURS, OPERATING_HOURS, VEHICLE_TYPE_CURFEW. "
                    + "VEHICLE_TYPE_CURFEW ruleValue format: TypeName:HH:mm (e.g. Truck:22:00).")
    @PostMapping
    public ResponseEntity<ApiResponse<BuildingRuleResponse>> create(
            @PathVariable String buildingId,
            @Valid @RequestBody CreateBuildingRuleRequest request) {
        BuildingRuleResponse created = buildingRuleService.create(buildingId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Building rule created successfully", created));
    }

    @Operation(summary = "Update building rule")
    @PutMapping("/{ruleId}")
    public ResponseEntity<ApiResponse<BuildingRuleResponse>> update(
            @PathVariable String buildingId,
            @PathVariable String ruleId,
            @Valid @RequestBody UpdateBuildingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Building rule updated successfully",
                buildingRuleService.update(ruleId, request)));
    }

    @Operation(summary = "Delete building rule")
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String buildingId,
            @PathVariable String ruleId) {
        buildingRuleService.delete(ruleId);
        return ResponseEntity.ok(ApiResponse.ok("Building rule deleted successfully", null));
    }
}
