package fpt.swp391.parkingmanagement.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.IncidentRequest;
import fpt.swp391.parkingmanagement.dto.IncidentResponse;
import fpt.swp391.parkingmanagement.dto.IncidentUpdateRequest;
import fpt.swp391.parkingmanagement.entity.Incident;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.IncidentRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.service.AuditLogService;
import fpt.swp391.parkingmanagement.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentServiceImpl implements IncidentService {

    public static final String RESOLUTION_ACTION_AUTHORIZE_CHECKOUT = "AUTHORIZE_CHECKOUT";
    public static final String RESOLUTION_ACTION_PROVIDE_VEHICLE_LOCATION = "PROVIDE_VEHICLE_LOCATION";
    public static final String RESOLUTION_ACTION_UPDATE_PAYMENT = "UPDATE_PAYMENT";
    public static final String RESOLUTION_ACTION_REJECT = "REJECT";
    public static final String RESOLUTION_ACTION_REASSIGN_SLOT = "REASSIGN_SLOT";
    public static final String RESOLUTION_ACTION_NO_SLOT_AVAILABLE = "NO_SLOT_AVAILABLE";

    private static final Set<String> ALLOWED_INCIDENT_TYPES = Set.of(
            "LOST_TICKET", "PLATE_MISMATCH", "OVERTIME", "WRONG_ZONE", "UNPAID_EXIT", "OTHER",
            "SLOT_CONFLICT", "RESERVATION_NO_SHOW", "PAYMENT_EXCEPTION",
            "UNAUTHORIZED_PARKING", "MAINTENANCE_CONFLICT");
    private static final Set<String> ALLOWED_STATUSES = Set.of("OPEN", "IN_PROGRESS", "PENDING", "RESOLVED", "CLOSED", "CANCELLED");
    private static final Set<String> DRIVER_REPORT_TYPES = Set.of(
            "DRIVER_LOST_TICKET", "DRIVER_CANNOT_FIND_VEHICLE",
            "DRIVER_INCORRECT_FEE", "DRIVER_SLOT_OCCUPIED");

    private final IncidentRepository incidentRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

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
        return updateIncidentStatus(staffEmail, incidentId, status, null);
    }

    @Override
    @Transactional
    public IncidentResponse updateIncidentStatus(String staffEmail, String incidentId, String status, IncidentUpdateRequest request) {
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

        if (request != null) {
            incident.setResolution(request.getResolution());
            incident.setResolutionAction(request.getResolutionAction());
            incident.setResolvedAt(LocalDateTime.now());
            incident.setResolvedBy(staffEmail);

            if ("RESOLVED".equals(normalized) && request.getResolutionAction() != null) {
                executeResolutionAction(incident, request);
            }
        }

        Incident saved = incidentRepository.save(incident);
        log.info("Incident {} status -> {} by {} with action {}", incidentId, normalized, staffEmail, request != null ? request.getResolutionAction() : "none");
        return toResponse(saved);
    }

    private void executeResolutionAction(Incident incident, IncidentUpdateRequest request) {
        ParkingSession session = incident.getSession();
        String action = request.getResolutionAction();

        switch (action) {
            case RESOLUTION_ACTION_AUTHORIZE_CHECKOUT:
                if (session != null) {
                    session.setIncidentAuthorized(true);
                    parkingSessionRepository.save(session);
                }
                break;

            case RESOLUTION_ACTION_UPDATE_PAYMENT:
                if (session != null && request.getAdjustedAmount() != null) {
                    session.setEstimatedFee(request.getAdjustedAmount());
                    session.setTotalFee(request.getAdjustedAmount());
                    parkingSessionRepository.save(session);
                }
                break;

            case RESOLUTION_ACTION_REASSIGN_SLOT:
                if (session != null && request.getNewSlotId() != null) {
                    ParkingSlot newSlot = parkingSlotRepository.findById(request.getNewSlotId())
                            .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + request.getNewSlotId()));

                    session.setSlot(newSlot);

                    Reservation reservation = session.getReservation();
                    if (reservation != null) {
                        reservation.setSlot(newSlot);
                        reservationRepository.save(reservation);
                    }

                    parkingSessionRepository.save(session);
                }
                break;

            case RESOLUTION_ACTION_PROVIDE_VEHICLE_LOCATION:
            case RESOLUTION_ACTION_REJECT:
            case RESOLUTION_ACTION_NO_SLOT_AVAILABLE:
            default:
                break;
        }
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
        return incidentRepository.findAllFetchingDetails().stream()
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

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getAllDriverReports() {
        return incidentRepository.findAllDriverReports().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Tao incident tu he thong (auto-create).
     * Dung trong IncidentAutoCreateJob.
     */
    @Transactional
    public IncidentResponse createSystemIncident(String sessionId, String incidentType, String description) {
        if (!ALLOWED_INCIDENT_TYPES.contains(incidentType)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Invalid incidentType for system: " + incidentType);
        }

        ParkingSession session = parkingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parking session not found: " + sessionId));

        Incident incident = new Incident();
        incident.setSession(session);
        incident.setIncidentType(incidentType);
        incident.setDescription(description);
        incident.setStatus("OPEN");
        incident.setReportSource("SYSTEM");

        Incident saved = incidentRepository.save(incident);
        log.info("System created incident {} for session {} type={}", saved.getIncidentId(), sessionId, incidentType);
        return toResponse(saved);
    }

    /**
     * Tao report tu Driver.
     */
    @Transactional
    public IncidentResponse createDriverReport(String driverEmail, IncidentRequest request) {
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST, "sessionId is required for driver report");
        }
        String incidentType = request.getIncidentType() == null ? "OTHER"
                : request.getIncidentType().trim().toUpperCase();
        if (!DRIVER_REPORT_TYPES.contains(incidentType)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Invalid incidentType for driver. Allowed: " + DRIVER_REPORT_TYPES);
        }

        ParkingSession session = parkingSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parking session not found: " + request.getSessionId()));

        Incident incident = new Incident();
        incident.setSession(session);
        incident.setIncidentType(incidentType);
        incident.setDescription(request.getDescription());
        incident.setStatus("OPEN");
        incident.setReportSource("DRIVER");
        incident.setReporterId(driverEmail);

        Incident saved = incidentRepository.save(incident);
        log.info("Driver {} created report {} for session {} type={}", driverEmail, saved.getIncidentId(), request.getSessionId(), incidentType);
        return toResponse(saved);
    }

    /**
     * Lay danh sach reports cua Driver.
     */
    @Transactional(readOnly = true)
    public List<IncidentResponse> getDriverReports(String driverEmail) {
        return incidentRepository.findByReporterId(driverEmail).stream()
                .map(this::toResponse)
                .toList();
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
                .reporterId(incident.getReporterId())
                .reportSource(incident.getReportSource())
                .resolution(incident.getResolution())
                .resolvedAt(incident.getResolvedAt())
                .resolvedBy(incident.getResolvedBy())
                .resolutionAction(incident.getResolutionAction())
                .build();
    }
}