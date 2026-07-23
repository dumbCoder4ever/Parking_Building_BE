package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.SystemConfigResponse;
import fpt.swp391.parkingmanagement.dto.UpdateSystemConfigRequest;
import fpt.swp391.parkingmanagement.entity.SystemConfig;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    public static final String GRACE_PERIOD_MINUTES = "GRACE_PERIOD_MINUTES";
    public static final String PAYMENT_REMINDER_MINUTES = "PAYMENT_REMINDER_MINUTES";
    public static final String MAX_PARKING_HOURS = "MAX_PARKING_HOURS";
    public static final String PEAK_HOUR_STDDEV_FACTOR = "PEAK_HOUR_STDDEV_FACTOR";
    public static final String OVERSTAY_NOTIFY_ENABLED = "OVERSTAY_NOTIFY_ENABLED";

    private static final Map<String, String[]> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(GRACE_PERIOD_MINUTES, new String[]{"15", "Grace period (minutes) after reservationStart before auto-expire"});
        DEFAULTS.put(PAYMENT_REMINDER_MINUTES, new String[]{"15", "Minutes after check-in before unpaid payment reminder"});
        DEFAULTS.put(MAX_PARKING_HOURS, new String[]{"24", "Default maximum parking duration (hours) for overstay alerts"});
        DEFAULTS.put(PEAK_HOUR_STDDEV_FACTOR, new String[]{"1.0", "Peak threshold = average + stdDev * factor"});
        DEFAULTS.put(OVERSTAY_NOTIFY_ENABLED, new String[]{"true", "Enable vehicle overstay notification job"});
    }

    private final SystemConfigRepository systemConfigRepository;
    private final AuditLogService auditLogService;

    @PostConstruct
    public void seedDefaults() {
        try {
            for (Map.Entry<String, String[]> e : DEFAULTS.entrySet()) {
                if (!systemConfigRepository.existsByConfigKeyIgnoreCase(e.getKey())) {
                    SystemConfig cfg = new SystemConfig();
                    cfg.setConfigKey(e.getKey());
                    cfg.setConfigValue(e.getValue()[0]);
                    cfg.setDescription(e.getValue()[1]);
                    systemConfigRepository.save(cfg);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not seed system_configs (table may be missing): {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<SystemConfigResponse> listAll() {
        return systemConfigRepository.findAll().stream()
                .sorted((a, b) -> a.getConfigKey().compareToIgnoreCase(b.getConfigKey()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getValue(String key) {
        String normalized = normalizeKey(key);
        return systemConfigRepository.findByConfigKeyIgnoreCase(normalized)
                .map(SystemConfig::getConfigValue)
                .orElseGet(() -> {
                    String[] def = DEFAULTS.get(normalized);
                    return def != null ? def[0] : null;
                });
    }

    public int getInt(String key, int defaultValue) {
        String raw = getValue(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String raw = getValue(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = getValue(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
    }

    @Transactional
    public SystemConfigResponse update(String configKey, UpdateSystemConfigRequest request) {
        String key = normalizeKey(configKey);
        if (!DEFAULTS.containsKey(key)) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST,
                    "Unknown configKey. Allowed: " + DEFAULTS.keySet());
        }
        SystemConfig cfg = systemConfigRepository.findByConfigKeyIgnoreCase(key)
                .orElseGet(() -> {
                    SystemConfig created = new SystemConfig();
                    created.setConfigKey(key);
                    created.setDescription(DEFAULTS.get(key)[1]);
                    return created;
                });
        String oldValue = cfg.getConfigValue();
        String newValue = request.getConfigValue().trim();
        validateValue(key, newValue);
        cfg.setConfigValue(newValue);
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            cfg.setDescription(request.getDescription().trim());
        }
        SystemConfig saved = systemConfigRepository.save(cfg);
        auditLogService.record(
                "SYSTEM_CONFIG_UPDATE",
                "SYSTEM_CONFIG",
                saved.getConfigId(),
                null,
                oldValue,
                newValue,
                "Updated " + key,
                null);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SystemConfigResponse getByKey(String configKey) {
        String key = normalizeKey(configKey);
        return systemConfigRepository.findByConfigKeyIgnoreCase(key)
                .map(this::toResponse)
                .orElseGet(() -> {
                    String[] def = DEFAULTS.get(key);
                    if (def == null) {
                        throw new ResourceNotFoundException("Config not found: " + key);
                    }
                    return SystemConfigResponse.builder()
                            .configKey(key)
                            .configValue(def[0])
                            .description(def[1])
                            .build();
                });
    }

    private void validateValue(String key, String value) {
        try {
            switch (key) {
                case GRACE_PERIOD_MINUTES, PAYMENT_REMINDER_MINUTES, MAX_PARKING_HOURS -> {
                    int n = Integer.parseInt(value);
                    if (n < 1 || n > 168) {
                        throw new BaseAPIException(ErrorCode.INVALID_REQUEST, key + " must be between 1 and 168");
                    }
                }
                case PEAK_HOUR_STDDEV_FACTOR -> {
                    double d = Double.parseDouble(value);
                    if (d < 0 || d > 5) {
                        throw new BaseAPIException(ErrorCode.INVALID_REQUEST, key + " must be between 0 and 5");
                    }
                }
                case OVERSTAY_NOTIFY_ENABLED -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)
                            && !"1".equals(value) && !"0".equals(value)) {
                        throw new BaseAPIException(ErrorCode.INVALID_REQUEST, key + " must be true/false");
                    }
                }
                default -> {
                }
            }
        } catch (NumberFormatException e) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST, "Invalid numeric value for " + key);
        }
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST, "configKey is required");
        }
        return key.trim().toUpperCase(Locale.ROOT);
    }

    private SystemConfigResponse toResponse(SystemConfig cfg) {
        return SystemConfigResponse.builder()
                .configId(cfg.getConfigId())
                .configKey(cfg.getConfigKey())
                .configValue(cfg.getConfigValue())
                .description(cfg.getDescription())
                .updatedAt(cfg.getUpdatedAt())
                .build();
    }
}
