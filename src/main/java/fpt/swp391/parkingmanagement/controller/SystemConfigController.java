package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.SystemConfigResponse;
import fpt.swp391.parkingmanagement.dto.UpdateSystemConfigRequest;
import fpt.swp391.parkingmanagement.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/manager/system-configs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @Operation(summary = "List system configurations")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemConfigResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(
                "System configs retrieved successfully",
                systemConfigService.listAll()));
    }

    @Operation(summary = "Get system config by key")
    @GetMapping("/{configKey}")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> get(@PathVariable String configKey) {
        return ResponseEntity.ok(ApiResponse.ok(
                "System config retrieved successfully",
                systemConfigService.getByKey(configKey)));
    }

    @Operation(
            summary = "Update system config",
            description = "Keys: GRACE_PERIOD_MINUTES, PAYMENT_REMINDER_MINUTES, MAX_PARKING_HOURS, "
                    + "PEAK_HOUR_STDDEV_FACTOR, OVERSTAY_NOTIFY_ENABLED")
    @PutMapping("/{configKey}")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> update(
            @PathVariable String configKey,
            @Valid @RequestBody UpdateSystemConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "System config updated successfully",
                systemConfigService.update(configKey, request)));
    }
}
