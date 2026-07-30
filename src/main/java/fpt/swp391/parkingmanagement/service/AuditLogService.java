package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    public void record(
            String actionType,
            String entityType,
            String entityId,
            String buildingId,
            String oldValue,
            String newValue,
            String description,
            String metadata) {
        // Audit logging disabled — no DB writes.
    }

    public void recordSystem(
            String actionType,
            String entityType,
            String entityId,
            String buildingId,
            String oldValue,
            String newValue,
            String description) {
        // Audit logging disabled — no DB writes.
    }

    public Page<AuditLogResponse> search(
            String buildingId,
            String actionType,
            String entityType,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        return Page.empty(pageable);
    }
}
