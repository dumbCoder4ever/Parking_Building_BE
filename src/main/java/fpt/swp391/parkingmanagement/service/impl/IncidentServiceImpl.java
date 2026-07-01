package fpt.swp391.parkingmanagement.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.IncidentRequest;
import fpt.swp391.parkingmanagement.dto.IncidentResponse;
import fpt.swp391.parkingmanagement.entity.Incident;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.IncidentRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentServiceImpl implements IncidentService {

    private static final Set<String> ALLOWED_INCIDENT_TYPES = Set.of(
            "LOST_TICKET", "PLATE_MISMATCH", "OVERTIME", "WRONG_ZONE", "UNPAID_EXIT", "OTHER");
    private static final Set<String> ALLOWED_STATUSES = Set.of("OPEN", "RESOLVED", "CANCELLED");

    private final IncidentRepository incidentRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public IncidentResponse createIncident(String staffEmail, IncidentRequest request) {
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST, "sessionId is required");
        }
        String incidentType = request.getIncidentType() == null ? "OTHER"
                : request.getIncidentType().trim().toUpperCase();
        if (!ALLOWED_INCIDENT_TYPES.contains(incidentType)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Invalid incidentType. Allowed: " + ALLOWED_INCIDENT_TYPES);
        }

        ParkingSession session = parkingSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parking session not found: " + request.getSessionId()));

        User staff = userRepository.findByEmail(staffEmail).orElse(null);

        Incident incident = new Incident();
        incident.setSession(session);
        incident.setIncidentType(incidentType);
        incident.setDescription(request.getDescription());
        incident.setStatus("OPEN");

        Incident saved = incidentRepository.save(incident);
        log.info("Incident created by staff {} for session {} type={}",
                staffEmail, request.getSessionId(), incidentType);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse updateIncidentStatus(String staffEmail, String incidentId, String status) {
        if (status == null) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST, "status is required");
        }
        String normalized = status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Invalid status. Allowed: " + ALLOWED_STATUSES);
        }

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
        incident.setStatus(normalized);
        Incident saved = incidentRepository.save(incident);
        log.info("Incident {} status -> {} by {}", incidentId, normalized, staffEmail);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getIncident(String staffEmail, String incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
        return toResponse(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getAllIncidents(String staffEmail) {
        return incidentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidentsBySession(String sessionId) {
        return incidentRepository.findBySessionSessionId(sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        return incidentRepository.countByStatus(status);
    }

    private IncidentResponse toResponse(Incident incident) {
        ParkingSession session = incident.getSession();
        return IncidentResponse.builder()
                .incidentId(incident.getIncidentId())
                .sessionId(session != null ? session.getSessionId() : null)
                .ticketCode(session != null && session.getTicket() != null
                        ? session.getTicket().getTicketCode() : null)
                .vehiclePlate(session != null && session.getVehicle() != null
                        ? session.getVehicle().getPlateNumber() : null)
                .incidentType(incident.getIncidentType())
                .description(incident.getDescription())
                .status(incident.getStatus())
                .createdAt(incident.getCreatedAt())
                .build();
    }
}