package fpt.swp391.parkingmanagement.job;

import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import fpt.swp391.parkingmanagement.dto.IncidentResponse;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentAutoCreateJob {
    private final IncidentService incidentService;
    private final ParkingSessionRepository parkingSessionRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void detectAndCreateIncidents() {
        log.debug("Running incident auto-create detection job...");
        try {
            checkSlotConflicts();
        } catch (Exception e) {
            log.error("Error checking slot conflicts", e);
        }
    }

    private void checkSlotConflicts() {
        List<Object[]> slotWithMultipleSessions = parkingSessionRepository.findSlotsWithMultipleActiveSessions();
        for (Object[] row : slotWithMultipleSessions) {
            String slotId = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            if (count > 1) {
                List<ParkingSession> sessions = parkingSessionRepository.findActiveSessionsBySlotId(slotId);
                for (ParkingSession session : sessions) {
                    if (session != null) {
                        createIncidentIfNotExists(session.getSessionId(), "SLOT_CONFLICT",
                                "Detected " + count + " active sessions on slot " + slotId);
                    }
                }
            }
        }
    }

    private void createIncidentIfNotExists(String sessionId, String incidentType, String description) {
        List<IncidentResponse> existing = incidentService.getIncidentsBySession(sessionId);
        boolean alreadyExists = existing.stream()
                .anyMatch(i -> incidentType.equals(i.getIncidentType())
                        && ("OPEN".equals(i.getStatus()) || "IN_PROGRESS".equals(i.getStatus())));
        if (!alreadyExists) {
            try {
                incidentService.createSystemIncident(sessionId, incidentType, description);
                log.info("Created {} incident for session {}", incidentType, sessionId);
            } catch (Exception e) {
                log.error("Failed to create incident for session {} type {}", sessionId, incidentType, e);
            }
        }
    }
}