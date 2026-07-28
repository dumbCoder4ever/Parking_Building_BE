package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.BuildingRuleResponse;
import fpt.swp391.parkingmanagement.dto.CreateBuildingRuleRequest;
import fpt.swp391.parkingmanagement.dto.UpdateBuildingRuleRequest;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.BuildingRule;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.BuildingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BuildingRuleService {

    public static final String CODE_NO_OVERNIGHT = "NO_OVERNIGHT";
    public static final String CODE_MAX_PARKING_HOURS = "MAX_PARKING_HOURS";
    public static final String CODE_OPERATING_HOURS = "OPERATING_HOURS";
    public static final String CODE_VEHICLE_TYPE_CURFEW = "VEHICLE_TYPE_CURFEW";

    private static final Set<String> ALLOWED_CODES = Set.of(
            CODE_NO_OVERNIGHT, CODE_MAX_PARKING_HOURS, CODE_OPERATING_HOURS, CODE_VEHICLE_TYPE_CURFEW);
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final String DEFAULT_PARKING_RULES =
            "Please reserve a parking slot in advance. Present your ticket code at check-in. Keep your ticket safe when leaving the parking lot.";

    private final BuildingRuleRepository buildingRuleRepository;
    private final BuildingRepository buildingRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<BuildingRuleResponse> listByBuilding(String buildingId) {
        findBuilding(buildingId);
        return buildingRuleRepository.findByBuildingBuildingIdOrderByCreatedAtDesc(buildingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BuildingRuleResponse> listActiveByBuilding(String buildingId) {
        findBuilding(buildingId);
        return buildingRuleRepository.findActiveByBuildingId(buildingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String resolveParkingRulesText(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) {
            return DEFAULT_PARKING_RULES;
        }
        List<BuildingRule> active = buildingRuleRepository.findActiveByBuildingId(buildingId);
        if (active.isEmpty()) {
            return DEFAULT_PARKING_RULES;
        }
        return active.stream()
                .map(BuildingRule::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" | "));
    }

    @Transactional
    public BuildingRuleResponse create(String buildingId, CreateBuildingRuleRequest request) {
        Building building = findBuilding(buildingId);
        String code = normalizeCode(request.getRuleCode());
        String status = normalizeStatus(request.getStatus() == null ? "ACTIVE" : request.getStatus());

        BuildingRule rule = new BuildingRule();
        rule.setBuilding(building);
        rule.setRuleCode(code);
        rule.setTitle(normalizeText(request.getTitle()));
        rule.setDescription(normalizeText(request.getDescription()));
        rule.setRuleValue(normalizeText(request.getRuleValue()));
        rule.setStatus(status);

        BuildingRule saved = buildingRuleRepository.save(rule);
        auditLogService.record(
                "BUILDING_RULE_CREATE",
                "BUILDING_RULE",
                saved.getRuleId(),
                buildingId,
                null,
                code + ":" + status,
                "Created building rule " + code,
                null);
        return toResponse(saved);
    }

    @Transactional
    public BuildingRuleResponse update(String ruleId, UpdateBuildingRuleRequest request) {
        BuildingRule rule = buildingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Building rule not found: " + ruleId));
        String oldStatus = rule.getStatus();
        rule.setTitle(normalizeText(request.getTitle()));
        rule.setDescription(normalizeText(request.getDescription()));
        rule.setRuleValue(normalizeText(request.getRuleValue()));
        rule.setStatus(normalizeStatus(request.getStatus()));

        BuildingRule saved = buildingRuleRepository.save(rule);
        String buildingId = saved.getBuilding() != null ? saved.getBuilding().getBuildingId() : null;
        auditLogService.record(
                "BUILDING_RULE_UPDATE",
                "BUILDING_RULE",
                saved.getRuleId(),
                buildingId,
                oldStatus,
                saved.getStatus(),
                "Updated building rule " + saved.getRuleCode(),
                null);
        return toResponse(saved);
    }

    @Transactional
    public void delete(String ruleId) {
        BuildingRule rule = buildingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Building rule not found: " + ruleId));
        String buildingId = rule.getBuilding() != null ? rule.getBuilding().getBuildingId() : null;
        String code = rule.getRuleCode();
        buildingRuleRepository.delete(rule);
        auditLogService.record(
                "BUILDING_RULE_DELETE",
                "BUILDING_RULE",
                ruleId,
                buildingId,
                code,
                null,
                "Deleted building rule " + code,
                null);
    }

    /**
     * Enforce ACTIVE rules for reservation / guest check-in start time.
     */
    @Transactional(readOnly = true)
    public void validateForEntry(Building building, VehicleType vehicleType, LocalDateTime startTime) {
        if (building == null || startTime == null) {
            return;
        }
        List<BuildingRule> rules = buildingRuleRepository.findActiveByBuildingId(building.getBuildingId());
        if (rules.isEmpty()) {
            return;
        }

        LocalTime time = startTime.toLocalTime();
        for (BuildingRule rule : rules) {
            String code = rule.getRuleCode() == null ? "" : rule.getRuleCode().trim().toUpperCase(Locale.ROOT);
            switch (code) {
                case CODE_OPERATING_HOURS, CODE_NO_OVERNIGHT -> enforceOperatingWindow(building, time, rule);
                case CODE_VEHICLE_TYPE_CURFEW -> enforceVehicleCurfew(vehicleType, time, rule);
                case CODE_MAX_PARKING_HOURS -> {
                    // Informational at reserve time; duration is computed at checkout.
                }
                default -> {
                }
            }
        }
    }

    private void enforceOperatingWindow(Building building, LocalTime time, BuildingRule rule) {
        LocalTime start = building.getOperatingStartTime();
        LocalTime end = building.getOperatingEndTime();
        if (start == null || end == null) {
            return;
        }
        boolean inside = !time.isBefore(start) && !time.isAfter(end);
        if (!inside) {
            throw new BaseAPIException(
                    ErrorCode.INVALID_REQUEST,
                    "Building rule violated (" + rule.getRuleCode() + "): outside operating hours "
                            + start + " - " + end);
        }
    }

    private void enforceVehicleCurfew(VehicleType vehicleType, LocalTime time, BuildingRule rule) {
        if (vehicleType == null || rule.getRuleValue() == null || rule.getRuleValue().isBlank()) {
            return;
        }
        // Format: TypeName:HH:mm  e.g. Truck:22:00
        String[] parts = rule.getRuleValue().split(":");
        if (parts.length < 3) {
            return;
        }
        String typeName = parts[0].trim();
        try {
            LocalTime curfew = LocalTime.of(Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            if (typeName.equalsIgnoreCase(vehicleType.getTypeName()) && !time.isBefore(curfew)) {
                throw new BaseAPIException(
                        ErrorCode.INVALID_REQUEST,
                        "Building rule violated (" + rule.getRuleCode() + "): "
                                + typeName + " not allowed after " + curfew);
            }
        } catch (NumberFormatException ignored) {
            // ignore malformed rule_value
        }
    }

    private Building findBuilding(String buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found: " + buildingId));
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !ALLOWED_CODES.contains(normalized)) {
            throw new BaseAPIException(
                    ErrorCode.INVALID_REQUEST,
                    "Invalid ruleCode. Allowed: " + ALLOWED_CODES);
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !STATUSES.contains(normalized)) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST, "Invalid status. Allowed: ACTIVE, INACTIVE");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private BuildingRuleResponse toResponse(BuildingRule rule) {
        return BuildingRuleResponse.builder()
                .ruleId(rule.getRuleId())
                .buildingId(rule.getBuilding() != null ? rule.getBuilding().getBuildingId() : null)
                .ruleCode(rule.getRuleCode())
                .title(rule.getTitle())
                .description(rule.getDescription())
                .ruleValue(rule.getRuleValue())
                .status(rule.getStatus())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
