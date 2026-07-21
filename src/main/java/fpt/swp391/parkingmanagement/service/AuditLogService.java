package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.AuditLogResponse;
import fpt.swp391.parkingmanagement.entity.AuditLog;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.repository.AuditLogRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Fail-soft: never breaks the business transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String actionType,
            String entityType,
            String entityId,
            String buildingId,
            String oldValue,
            String newValue,
            String description,
            String metadata) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActionType(actionType);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setBuildingId(buildingId);
            entry.setOldValue(trim(oldValue, 500));
            entry.setNewValue(trim(newValue, 500));
            entry.setDescription(trim(description, 500));
            entry.setMetadata(metadata);

            Actor actor = resolveActor();
            entry.setUserId(actor.userId());
            entry.setUsername(actor.username());

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log action={}: {}", actionType, e.getMessage());
        }
    }

    public void recordSystem(
            String actionType,
            String entityType,
            String entityId,
            String buildingId,
            String oldValue,
            String newValue,
            String description) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActionType(actionType);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setBuildingId(buildingId);
            entry.setOldValue(trim(oldValue, 500));
            entry.setNewValue(trim(newValue, 500));
            entry.setDescription(trim(description, 500));
            entry.setUserId(null);
            entry.setUsername("SYSTEM");
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write system audit log action={}: {}", actionType, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(
            String buildingId,
            String actionType,
            String entityType,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        return auditLogRepository
                .search(blankToNull(buildingId), blankToNull(actionType), blankToNull(entityType), from, to, pageable)
                .map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return AuditLogResponse.builder()
                .logId(a.getLogId())
                .actionType(a.getActionType())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .userId(a.getUserId())
                .username(a.getUsername())
                .description(a.getDescription())
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .buildingId(a.getBuildingId())
                .metadata(a.getMetadata())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private Actor resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            return new Actor(null, "SYSTEM");
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElse(null);
        if (user == null) {
            return new Actor(null, username);
        }
        return new Actor(user.getUserId(), user.getUsername());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record Actor(String userId, String username) {}
}
