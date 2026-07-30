package fpt.swp391.parkingmanagement.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.AvailableSlotResponse;
import fpt.swp391.parkingmanagement.dto.IncidentRequest;
import fpt.swp391.parkingmanagement.dto.IncidentResponse;
import fpt.swp391.parkingmanagement.dto.IncidentUpdateRequest;
import fpt.swp391.parkingmanagement.dto.LatestReservationResponse;
import fpt.swp391.parkingmanagement.dto.SessionEvidenceResponse;
import fpt.swp391.parkingmanagement.dto.SlotAvailabilityCheckResponse;
import fpt.swp391.parkingmanagement.dto.VerifyVehicleRequest;
import fpt.swp391.parkingmanagement.dto.VerifyVehicleResponse;
import fpt.swp391.parkingmanagement.entity.Incident;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.IncidentRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.PaymentRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.repository.BuildingStaffRepository;
import fpt.swp391.parkingmanagement.service.AuditLogService;
import fpt.swp391.parkingmanagement.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;

import fpt.swp391.parkingmanagement.entity.Payment;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentServiceImpl implements IncidentService {

    // Resolution action constants
    public static final String RESOLUTION_ACTION_AUTHORIZE_CHECKOUT = "AUTHORIZE_CHECKOUT";
    public static final String RESOLUTION_ACTION_PROVIDE_VEHICLE_LOCATION = "PROVIDE_VEHICLE_LOCATION";
    public static final String RESOLUTION_ACTION_UPDATE_PAYMENT = "UPDATE_PAYMENT";
    public static final String RESOLUTION_ACTION_REJECT = "REJECT";
    public static final String RESOLUTION_ACTION_REASSIGN_SLOT = "REASSIGN_SLOT";
    public static final String RESOLUTION_ACTION_NO_SLOT_AVAILABLE = "NO_SLOT_AVAILABLE";

    // Active reservation statuses
    private static final Set<String> ACTIVE_RESERVATION_STATUSES = Set.of("PENDING", "APPROVED", "CHECKED_IN");

    private static final Set<String> ACTIVE_SESSION_STATUSES = Set.of("ACTIVE", "PENDING_PAYMENT");

    private static final Set<String> ALLOWED_INCIDENT_TYPES = Set.of(
            "LOST_TICKET", "PLATE_MISMATCH", "OVERTIME", "WRONG_ZONE", "UNPAID_EXIT", "OTHER",
            "SLOT_CONFLICT", "RESERVATION_NO_SHOW", "PAYMENT_EXCEPTION",
            "UNAUTHORIZED_PARKING", "MAINTENANCE_CONFLICT");

    private static final Set<String> DRIVER_REPORT_TYPES = Set.of(
            "DRIVER_LOST_TICKET", "DRIVER_CANNOT_FIND_VEHICLE",
            "DRIVER_INCORRECT_FEE", "DRIVER_SLOT_OCCUPIED");

    // Max multiplier for adjusted payment (10x estimated fee)
    private static final BigDecimal MAX_PAYMENT_MULTIPLIER = BigDecimal.TEN;

    private final IncidentRepository incidentRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final BuildingStaffRepository buildingStaffRepository;
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

        Incident incident = new Incident();
        incident.setSession(session);
        incident.setIncidentType(incidentType);
        incident.setDescription(request.getDescription());
        incident.setStatus("OPEN");
        incident.setVerificationResult("PENDING");

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
        if (!isValidStatus(normalized)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Invalid status. Allowed: OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED");
        }

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        // Kiem tra staff co quyen xu ly incident nay khong
        validateStaffBuildingAccess(staffEmail, incident);

        // Enforce workflow: OPEN -> IN_PROGRESS -> RESOLVED
        validateStatusTransition(incident.getStatus(), normalized, incident.getIncidentType());

        // Save previous status for tracking
        incident.setPreviousStatus(incident.getStatus());
        incident.setStatus(normalized);

        if (request != null) {
            incident.setResolutionAction(request.getResolutionAction());
            incident.setResolvedAt(LocalDateTime.now());
            incident.setResolvedBy(staffEmail);

            // THEM: Handle CANCELLED status with cancelReason
            if ("CANCELLED".equals(normalized)) {
                if (request.getCancelReason() == null || request.getCancelReason().isBlank()) {
                    throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                            "Cancel reason is required when cancelling an incident");
                }
                incident.setResolution(request.getCancelReason());
            } else {
                incident.setResolution(request.getResolution());
            }

            if ("RESOLVED".equals(normalized) && request.getResolutionAction() != null) {
                executeResolutionAction(incident, request);
            }
            if ("RESOLVED".equals(normalized) && request.getAdjustedAmount() != null) {
                applyAdjustedSessionFee(incident, request.getAdjustedAmount());
            }
        }

        Incident saved = incidentRepository.save(incident);
        log.info("Incident {} status -> {} by {} with action {}", incidentId, normalized, staffEmail, request != null ? request.getResolutionAction() : "none");
        return toResponse(saved);
    }

    /**
     * Validate status transition based on workflow rules.
     * Valid transitions:
     * - OPEN -> IN_PROGRESS (OK)
     * - OPEN -> CANCELLED (OK - driver/system cancellation)
     * - IN_PROGRESS -> RESOLVED (OK)
     * - IN_PROGRESS -> CANCELLED (OK - staff cancellation)
     * - RESOLVED -> CLOSED (OK)
     * Invalid transitions:
     * - OPEN -> RESOLVED (NOT ALLOWED - must go through IN_PROGRESS)
     * - OPEN -> CLOSED (NOT ALLOWED - must resolve first)
     * - IN_PROGRESS -> CLOSED (NOT ALLOWED - must resolve first)
     * - RESOLVED -> CANCELLED (NOT ALLOWED - already resolved)
     * - CLOSED/CANCELLED -> any (NOT ALLOWED)
     */
    private void validateStatusTransition(String currentStatus, String newStatus, String incidentType) {
        // Da closed/cancelled thi khong the thay doi
        if ("CLOSED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Cannot change status of a closed or cancelled incident.");
        }

        // OPEN khong duoc nhay thang sang RESOLVED/CLOSED
        if ("OPEN".equals(currentStatus)) {
            if ("RESOLVED".equals(newStatus) || "CLOSED".equals(newStatus)) {
                throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                        "Must transition through IN_PROGRESS first.");
            }
        }

        // IN_PROGRESS khong duoc nhay thang sang CLOSED
        if ("IN_PROGRESS".equals(currentStatus) && "CLOSED".equals(newStatus)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Must RESOLVE incident before closing.");
        }

        // RESOLVED chi duoc sang CLOSED, khong duoc CANCELLED
        if ("RESOLVED".equals(currentStatus) && "CANCELLED".equals(newStatus)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Cannot cancel a resolved incident.");
        }
    }

    /**
     * Check if status is valid.
     */
    private boolean isValidStatus(String status) {
        return Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED").contains(status);
    }

    /**
     * Execute resolution action based on action type.
     */
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
                if (request.getAdjustedAmount() != null) {
                    applyAdjustedSessionFee(incident, request.getAdjustedAmount());
                } else {
                    log.info("Payment adjustment for incident {} (no adjustedAmount provided)", incident.getIncidentId());
                }
                break;

            case RESOLUTION_ACTION_REASSIGN_SLOT:
                if (session != null && request.getNewSlotId() != null) {
                    validateAndExecuteSlotReassignment(incident, session, request);
                }
                break;

            case RESOLUTION_ACTION_PROVIDE_VEHICLE_LOCATION:
                // Just log - no special action needed
                log.info("Providing vehicle location for incident {}", incident.getIncidentId());
                break;

            case RESOLUTION_ACTION_REJECT:
                // Just log - incident will be marked as CANCELLED
                log.info("Incident {} rejected by staff", incident.getIncidentId());
                break;

            case RESOLUTION_ACTION_NO_SLOT_AVAILABLE:
                // Just log - staff indicates no slot available
                log.info("No slot available for incident {}", incident.getIncidentId());
                break;

            default:
                log.warn("Unknown resolution action: {}", action);
        }
    }

    /**
     * Apply staff-adjusted fee to session (and linked reservation).
     */
    private void applyAdjustedSessionFee(Incident incident, BigDecimal amount) {
        ParkingSession session = incident.getSession();
        if (session == null || amount == null) {
            return;
        }
        validateAdjustedAmount(session, amount);
        session.setEstimatedFee(amount);
        session.setTotalFee(amount);
        parkingSessionRepository.save(session);

        Reservation reservation = session.getReservation();
        if (reservation != null) {
            reservation.setEstimatedFee(amount);
            reservationRepository.save(reservation);
        }
        syncSessionPaymentAmounts(session, amount);
        log.info("Adjusted session {} fee to {} via incident {}", session.getSessionId(), amount, incident.getIncidentId());
    }

    private void syncSessionPaymentAmounts(ParkingSession session, BigDecimal amount) {
        List<Payment> payments = paymentRepository.findBySessionSessionIdAndPaymentStatusInOrderByCreatedAtDesc(
                session.getSessionId(),
                List.of("PAID", "CONFIRMED", "SUCCESS", "PENDING"),
                PageRequest.of(0, 20));
        for (Payment payment : payments) {
            payment.setAmount(amount);
            paymentRepository.save(payment);
        }
        if (!payments.isEmpty()) {
            log.info("Synced {} payment record(s) for session {} to amount {}",
                    payments.size(), session.getSessionId(), amount);
        }
    }

    /**
     * Validate adjusted payment amount.
     * - Amount must not be negative
     * - Amount must not exceed 10x the estimated fee
     */
    private void validateAdjustedAmount(ParkingSession session, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Adjusted amount cannot be negative");
        }

        BigDecimal estimatedFee = session.getEstimatedFee();
        if (estimatedFee == null) {
            estimatedFee = BigDecimal.ZERO;
        }

        BigDecimal maxAllowed = estimatedFee.multiply(MAX_PAYMENT_MULTIPLIER);
        if (amount.compareTo(maxAllowed) > 0) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Adjusted amount exceeds maximum allowed (" + maxAllowed + "). Please verify the amount.");
        }
    }

    /**
     * Validate and execute slot reassignment.
     * - New slot must be AVAILABLE
     * - New slot must not have active reservation
     * - New slot must be in the same building
     * - New slot must have the same vehicle type as driver's reservation
     */
    private void validateAndExecuteSlotReassignment(Incident incident, ParkingSession session, IncidentUpdateRequest request) {
        ParkingSlot currentSlot = session.getSlot();
        if (currentSlot == null) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Session does not have a slot assigned");
        }

        ParkingSlot newSlot = parkingSlotRepository.findById(request.getNewSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + request.getNewSlotId()));

        // Validation 1: New slot must be AVAILABLE
        if (!"AVAILABLE".equals(newSlot.getSlotStatus())) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Slot is not available. Current status: " + newSlot.getSlotStatus());
        }

        // Validation 2: New slot must not have active reservation
        boolean hasActiveReservation = reservationRepository.existsActiveReservationBySlotId(newSlot.getSlotId());
        if (hasActiveReservation) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Slot has an active reservation. Cannot reassign to this slot.");
        }

        // Validation 3: New slot must be in the same building
        String oldBuildingId = currentSlot.getZone().getFloor().getBuilding().getBuildingId();
        String newBuildingId = newSlot.getZone().getFloor().getBuilding().getBuildingId();
        if (!oldBuildingId.equals(newBuildingId)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Replacement slot must be in the same building. Current building: " + oldBuildingId);
        }

        // Validation 4: New slot must have same vehicle type as driver's reservation
        // If driver has an active reservation, validate vehicle type
        String driverUserId = resolveDriverUserId(session);
        if (driverUserId != null) {
            Reservation reservationForType = resolveReservationForIncident(session);
            if (reservationForType != null
                    && reservationForType.getSlot() != null
                    && reservationForType.getSlot().getZone() != null
                    && reservationForType.getSlot().getZone().getFloor() != null
                    && reservationForType.getSlot().getZone().getFloor().getVehicleType() != null) {
                String reservationVehicleTypeId = reservationForType.getSlot().getZone().getFloor().getVehicleType().getVehicleTypeId();
                String newSlotVehicleTypeId = newSlot.getZone().getFloor().getVehicleType().getVehicleTypeId();
                if (!reservationVehicleTypeId.equals(newSlotVehicleTypeId)) {
                    throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                            "Replacement slot must have the same vehicle type as driver's reservation. "
                                    + "Expected vehicle type: " + reservationVehicleTypeId
                                    + ", new slot vehicle type: " + newSlotVehicleTypeId);
                }
            }
        }

        // Execute reassignment
        session.setSlot(newSlot);

        Reservation reservation = session.getReservation();
        if (reservation != null) {
            reservation.setSlot(newSlot);
            reservationRepository.save(reservation);
        }

        parkingSessionRepository.save(session);
        log.info("Reassigned slot for session {} from {} to {}",
                session.getSessionId(), currentSlot.getSlotId(), newSlot.getSlotId());
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getIncident(String staffEmail, String incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
        validateStaffBuildingAccess(staffEmail, incident);
        return toResponse(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getAllIncidents(String staffEmail) {
        Set<String> authorizedBuildingIds = getStaffAuthorizedBuildingIds(staffEmail);
        return incidentRepository.findAllFetchingDetails().stream()
                .filter(incident -> resolveIncidentBuildingId(incident)
                        .map(authorizedBuildingIds::contains)
                        .orElse(false))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Resolve buildingId cua incident tu session slot hoac reservation slot (fallback).
     * Neu khong lay duoc buildingId (session chua co slot, khong co reservation) -> empty.
     */
    private java.util.Optional<String> resolveIncidentBuildingId(Incident incident) {
        ParkingSession session = incident.getSession();
        if (session == null) return java.util.Optional.empty();

        // Uu tien 1: session.slot
        if (session.getSlot() != null
                && session.getSlot().getZone() != null
                && session.getSlot().getZone().getFloor() != null
                && session.getSlot().getZone().getFloor().getBuilding() != null) {
            return java.util.Optional.of(
                    session.getSlot().getZone().getFloor().getBuilding().getBuildingId());
        }

        // Fallback 2: session.reservation.slot
        if (session.getReservation() != null
                && session.getReservation().getSlot() != null
                && session.getReservation().getSlot().getZone() != null
                && session.getReservation().getSlot().getZone().getFloor() != null
                && session.getReservation().getSlot().getZone().getFloor().getBuilding() != null) {
            return java.util.Optional.of(
                    session.getReservation().getSlot().getZone().getFloor().getBuilding().getBuildingId());
        }

        // Fallback 3: latest active reservation cua driver
        String driverUserId = resolveDriverUserId(session);
        if (driverUserId != null) {
            return reservationRepository.findFirstLatestActiveReservationByUserId(driverUserId)
                    .filter(r -> r.getSlot() != null
                            && r.getSlot().getZone() != null
                            && r.getSlot().getZone().getFloor() != null
                            && r.getSlot().getZone().getFloor().getBuilding() != null)
                    .map(r -> r.getSlot().getZone().getFloor().getBuilding().getBuildingId());
        }

        return java.util.Optional.empty();
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
    public List<IncidentResponse> getAllDriverReports(String staffEmail, String buildingId) {
        String normalizedBuildingId = buildingId != null ? buildingId.trim() : null;
        if (normalizedBuildingId != null && normalizedBuildingId.isBlank()) {
            normalizedBuildingId = null;
        }
        final String filterBuildingId = normalizedBuildingId;

        if (filterBuildingId != null) {
            validateStaffCanAccessBuilding(staffEmail, filterBuildingId);
        }

        Set<String> authorizedBuildingIds = getStaffAuthorizedBuildingIds(staffEmail);
        boolean managerOrAdmin = isManagerOrAdmin(staffEmail);

        return incidentRepository.findAllDriverReports().stream()
                .filter(incident -> {
                    java.util.Optional<String> incidentBuildingId = resolveIncidentBuildingId(incident);
                    if (incidentBuildingId.isEmpty()) {
                        return false;
                    }
                    if (filterBuildingId != null) {
                        return filterBuildingId.equals(incidentBuildingId.get());
                    }
                    if (managerOrAdmin) {
                        return true;
                    }
                    return authorizedBuildingIds.contains(incidentBuildingId.get());
                })
                .map(this::toResponse)
                .toList();
    }

    /**
     * Verify vehicle ownership for DRIVER_LOST_TICKET incident.
     * Compares provided plate number and ticket code with session data.
     */
    @Transactional
    public VerifyVehicleResponse verifyVehicleOwnership(String incidentId, VerifyVehicleRequest request, String staffEmail) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        // Kiem tra staff co quyen xu ly incident nay
        validateStaffBuildingAccess(staffEmail, incident);

        // Only allow verification for DRIVER_LOST_TICKET incidents
        if (!"DRIVER_LOST_TICKET".equals(incident.getIncidentType())) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Vehicle verification is only applicable for DRIVER_LOST_TICKET incidents");
        }

        ParkingSession session = incident.getSession();
        if (session == null) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Incident does not have an associated session");
        }

        validateSessionActiveForVerification(session);

        String sessionPlateNumber = null;
        String sessionTicketCode = null;

        if (session.getVehicle() != null) {
            sessionPlateNumber = session.getVehicle().getPlateNumber();
        }
        if (session.getTicket() != null) {
            sessionTicketCode = session.getTicket().getTicketCode();
        }

        String providedPlateNumber = request.getPlateNumber();
        String providedTicketCode = request.getTicketCode();
        String sessionDriverEmail = resolveDriverEmail(session);
        boolean driverOwnershipVerified = isDriverOwnershipVerified(incident, sessionDriverEmail);

        // Perform verification
        boolean plateMatch = sessionPlateNumber != null &&
                sessionPlateNumber.equalsIgnoreCase(providedPlateNumber);
        boolean ticketMatch = sessionTicketCode != null &&
                sessionTicketCode.equalsIgnoreCase(providedTicketCode);

        // Determine result
        // If ticketCode is provided, both must match. Otherwise, only plate must match.
        // For driver reports, reporter must match session driver account when resolvable.
        boolean overallMatch;
        String resultMessage;

        if (providedTicketCode != null && !providedTicketCode.isBlank()) {
            overallMatch = plateMatch && ticketMatch && driverOwnershipVerified;
            resultMessage = overallMatch
                    ? "Vehicle ownership verified successfully"
                    : buildVerificationFailureMessage(plateMatch, ticketMatch, driverOwnershipVerified, true);
        } else {
            overallMatch = plateMatch && driverOwnershipVerified;
            resultMessage = overallMatch
                    ? "Vehicle ownership verified successfully (plate number matches)"
                    : buildVerificationFailureMessage(plateMatch, true, driverOwnershipVerified, false);
        }

        String verificationResult = overallMatch ? "MATCH" : "MISMATCH";

        // Update incident with verification result
        incident.setVerifiedPlateNumber(providedPlateNumber);
        incident.setVerifiedTicketCode(providedTicketCode);
        incident.setVerificationResult(verificationResult);
        incident.setVerifiedAt(LocalDateTime.now());
        incident.setVerifiedBy(staffEmail);
        incidentRepository.save(incident);

        log.info("Vehicle verification for incident {}: {} (staff: {})",
                incidentId, verificationResult, staffEmail);

        return VerifyVehicleResponse.builder()
                .incidentId(incidentId)
                .verificationResult(verificationResult)
                .sessionPlateNumber(sessionPlateNumber)
                .sessionTicketCode(sessionTicketCode)
                .providedPlateNumber(providedPlateNumber)
                .providedTicketCode(providedTicketCode)
                .checkinVehicleImage(session.getCheckinVehicleImage())
                .checkoutVehicleImage(session.getCheckoutVehicleImage())
                .driverEmail(sessionDriverEmail)
                .driverOwnershipVerified(driverOwnershipVerified)
                .message(resultMessage)
                .build();
    }

    /**
     * Primary evidence for staff: active parking session linked to the incident.
     * Reservation (if any) is returned as supplementary context only.
     */
    @Override
    @Transactional(readOnly = true)
    public SessionEvidenceResponse getSessionEvidenceForIncident(String incidentId, String staffEmail) {
        Incident incident = incidentRepository.findByIdFetchingFullChain(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        validateStaffBuildingAccessFromIncident(incident, staffEmail);

        ParkingSession session = incident.getSession();
        if (session == null) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Incident does not have an associated session");
        }

        User driver = resolveDriverUser(session);
        ParkingSlot slot = session.getSlot();
        Reservation supplementaryReservation = resolveReservationForIncident(session);

        SessionEvidenceResponse.SessionEvidenceResponseBuilder builder = SessionEvidenceResponse.builder()
                .incidentId(incidentId)
                .sessionId(session.getSessionId())
                .sessionStatus(session.getSessionStatus())
                .sessionActive(isActiveSession(session))
                .checkinTime(session.getCheckinTime())
                .checkinVehicleImage(session.getCheckinVehicleImage())
                .checkoutVehicleImage(session.getCheckoutVehicleImage())
                .ticketCode(session.getTicket() != null ? session.getTicket().getTicketCode() : null);

        if (session.getVehicle() != null) {
            Vehicle vehicle = session.getVehicle();
            builder.vehicleId(vehicle.getVehicleId())
                    .vehiclePlate(vehicle.getPlateNumber());
            if (vehicle.getVehicleType() != null) {
                builder.vehicleType(vehicle.getVehicleType().getTypeName());
            }
        }

        if (driver != null) {
            builder.driverUserId(driver.getUserId())
                    .driverEmail(driver.getEmail())
                    .driverFullName(driver.getFullName());
        }

        if ("DRIVER".equals(incident.getReportSource()) && incident.getReporterId() != null && driver != null) {
            builder.driverMatchesReporter(
                    incident.getReporterId().equalsIgnoreCase(driver.getEmail()));
        }

        if (slot != null) {
            builder.slotId(slot.getSlotId()).slotName(slot.getSlotName());
            if (slot.getZone() != null) {
                builder.zoneId(slot.getZone().getZoneId()).zoneName(slot.getZone().getZoneName());
                if (slot.getZone().getFloor() != null) {
                    Floor floor = slot.getZone().getFloor();
                    builder.floorId(floor.getFloorId())
                            .floorName(floor.getFloorName())
                            .floorLevel(floor.getFloorLevel());
                    if (floor.getBuilding() != null) {
                        builder.buildingId(floor.getBuilding().getBuildingId())
                                .buildingName(floor.getBuilding().getBuildingName());
                    }
                }
            }
        }

        java.math.BigDecimal estimatedFee = session.getEstimatedFee() != null
                ? session.getEstimatedFee() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalFee = session.getTotalFee() != null
                ? session.getTotalFee() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal displayTotalFee = totalFee.compareTo(java.math.BigDecimal.ZERO) > 0
                ? totalFee : estimatedFee;

        builder.sessionEstimatedFee(estimatedFee)
                .sessionTotalFee(displayTotalFee)
                .sessionPaymentStatus(session.getPaymentStatus() != null
                        ? session.getPaymentStatus() : "UNPAID");

        if (supplementaryReservation != null) {
            builder.reservation(toLatestReservationResponse(supplementaryReservation, session));
        }

        return builder.build();
    }

    /**
     * Check slot availability for reassignment.
     * Validates that the slot is available and can be used as replacement.
     */
    @Transactional(readOnly = true)
    public SlotAvailabilityCheckResponse checkSlotAvailabilityForReassignment(String incidentId, String newSlotId, String staffEmail) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        // Kiem tra staff co quyen xu ly incident nay
        validateStaffBuildingAccess(staffEmail, incident);

        ParkingSession session = incident.getSession();
        if (session == null || session.getSlot() == null) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Incident session does not have a slot assigned");
        }

        ParkingSlot currentSlot = session.getSlot();
        ParkingSlot newSlot = parkingSlotRepository.findById(newSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + newSlotId));

        String currentBuildingId = currentSlot.getZone().getFloor().getBuilding().getBuildingId();
        String newBuildingId = newSlot.getZone().getFloor().getBuilding().getBuildingId();
        boolean isInSameBuilding = currentBuildingId.equals(newBuildingId);

        boolean isAvailable = "AVAILABLE".equals(newSlot.getSlotStatus());
        boolean hasActiveReservation = reservationRepository.existsActiveReservationBySlotId(newSlotId);

        // Check vehicle type - slot must match driver's reservation vehicle type
        final boolean[] isSameVehicleType = {true};
        String driverUserId = resolveDriverUserId(session);
        if (driverUserId != null) {
            Reservation reservationForType = resolveReservationForIncident(session);
            if (reservationForType != null
                    && reservationForType.getSlot() != null
                    && reservationForType.getSlot().getZone() != null
                    && reservationForType.getSlot().getZone().getFloor() != null
                    && reservationForType.getSlot().getZone().getFloor().getVehicleType() != null
                    && newSlot.getZone().getFloor().getVehicleType() != null) {
                String reservationVehicleTypeId = reservationForType.getSlot().getZone().getFloor().getVehicleType().getVehicleTypeId();
                String newSlotVehicleTypeId = newSlot.getZone().getFloor().getVehicleType().getVehicleTypeId();
                if (!reservationVehicleTypeId.equals(newSlotVehicleTypeId)) {
                    isSameVehicleType[0] = false;
                }
            }
        }

        String message;
        if (!isAvailable) {
            message = "Slot is not available. Current status: " + newSlot.getSlotStatus();
        } else if (hasActiveReservation) {
            message = "Slot has an active reservation";
        } else if (!isInSameBuilding) {
            message = "Slot is in a different building";
        } else if (!isSameVehicleType[0]) {
            message = "Slot vehicle type does not match driver's reservation vehicle type";
        } else {
            message = "Slot is available for reassignment";
        }

        return SlotAvailabilityCheckResponse.builder()
                .slotId(newSlotId)
                .slotName(newSlot.getSlotName())
                .isAvailable(isAvailable && !hasActiveReservation && isInSameBuilding && isSameVehicleType[0])
                .hasActiveReservation(hasActiveReservation)
                .isInSameBuilding(isInSameBuilding)
                .isSameVehicleType(isSameVehicleType[0])
                .message(message)
                .build();
    }

    /**
     * Tao incident tu he thong (auto-create).
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
        incident.setVerificationResult("PENDING");

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
        incident.setVerificationResult("PENDING");

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

    /**
     * Lay thong tin reservation moi nhat (PENDING/APPROVED/CHECKED_IN) cua driver theo incident.
     * Dung lam bang chung cho 4 flow: mat ve, sai phi, slot bi chiem, khong tim thay xe.
     * TOI UU: Su dung findByIdFetchingFullChain de tranh N+1 queries.
     */
    @Override
    @Transactional(readOnly = true)
    public LatestReservationResponse getLatestReservationForIncident(String incidentId, String staffEmail) {
        // TOI UU: Lay incident voi full chain trong 1 query thay vi N+1
        Incident incident = incidentRepository.findByIdFetchingFullChain(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        // Kiem tra staff co quyen xu ly incident nay
        validateStaffBuildingAccessFromIncident(incident, staffEmail);

        ParkingSession session = incident.getSession();
        if (session == null) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Incident does not have an associated session");
        }

        Reservation reservation = resolveReservationForIncident(session);
        if (reservation == null) {
            throw new BaseAPIException(ErrorCode.RESERVATION_NOT_FOUND,
                    "Driver has no active reservation. Use session-evidence endpoint for session-based verification.");
        }

        return toLatestReservationResponse(reservation, session);
    }

    /**
     * Lay danh sach slot AVAILABLE trong zone cua driver (cung vehicle type, cung building) de staff chon.
     * Logic don gian: chi lay slot co status = AVAILABLE, bo qua cac slot khac (PENDING_EXIT, OCCUPIED, ...).
     */
    @Override
    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> getAvailableSlotsForReassign(String incidentId, String staffEmail) {
        // TOI UU: Lay incident voi full chain trong 1 query thay vi N+1
        Incident incident = incidentRepository.findByIdFetchingFullChain(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        // Kiem tra staff co quyen xu ly incident nay
        validateStaffBuildingAccessFromIncident(incident, staffEmail);

        ParkingSession session = incident.getSession();
        if (session == null) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Incident does not have an associated session");
        }

        String driverUserId = resolveDriverUserId(session);
        if (driverUserId == null) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Cannot resolve driver user for this session");
        }

        Reservation reservation = resolveReservationForIncident(session);
        if (reservation == null) {
            throw new BaseAPIException(ErrorCode.RESERVATION_NOT_FOUND,
                    "Driver has no active reservation. Cannot suggest slots.");
        }

        // Lay zone/floor/building tu reservation, fallback qua session neu reservation da CHECKED_IN
        ParkingSlot sourceSlot = reservation.getSlot() != null
                ? reservation.getSlot()
                : (session.getSlot() != null ? session.getSlot() : null);

        if (sourceSlot == null || sourceSlot.getZone() == null
                || sourceSlot.getZone().getFloor() == null) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Latest reservation has no slot/floor assigned");
        }

        Floor sourceFloor = sourceSlot.getZone().getFloor();
        String vehicleTypeId = sourceFloor.getVehicleType().getVehicleTypeId();
        String floorId = sourceFloor.getFloorId();

        // Lay current session slot id de loai tru
        String currentSlotId = session.getSlot() != null ? session.getSlot().getSlotId() : null;

        // Lay chi slot AVAILABLE trong zone cung vehicle type
        List<ParkingSlot> slots = parkingSlotRepository
                .findAvailableByFloorAndVehicleType(floorId, vehicleTypeId, currentSlotId);

        // Lay cac slotId co active reservation trong 1 query
        java.util.Set<String> slotIds = slots.stream()
                .map(ParkingSlot::getSlotId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> activeSlotIds = new java.util.HashSet<>(
                reservationRepository.findActiveSlotIdsBySlotIds(slotIds));

        // Loc bo slot co active reservation (PENDING/APPROVED)
        return slots.stream()
                .filter(s -> !activeSlotIds.contains(s.getSlotId()))
                .map(s -> toAvailableSlotResponse(s, false, true))
                .toList();
    }

    /**
     * Resolve driver userId tu session (uu tien reservation gan nhat, neu khong co thi qua vehicle.user).
     */
    private String resolveDriverUserId(ParkingSession session) {
        User driver = resolveDriverUser(session);
        return driver != null ? driver.getUserId() : null;
    }

    private User resolveDriverUser(ParkingSession session) {
        if (session.getReservation() != null && session.getReservation().getUser() != null) {
            return session.getReservation().getUser();
        }
        if (session.getVehicle() != null && session.getVehicle().getUser() != null) {
            return session.getVehicle().getUser();
        }
        return null;
    }

    private String resolveDriverEmail(ParkingSession session) {
        User driver = resolveDriverUser(session);
        return driver != null ? driver.getEmail() : null;
    }

    /**
     * Uu tien reservation gan voi session, fallback reservation active moi nhat cua driver.
     */
    private Reservation resolveReservationForIncident(ParkingSession session) {
        Reservation sessionReservation = session.getReservation();
        if (sessionReservation != null
                && isActiveReservationStatus(sessionReservation.getReservationStatus())) {
            return sessionReservation;
        }
        String driverUserId = resolveDriverUserId(session);
        if (driverUserId == null) {
            return null;
        }
        return reservationRepository.findFirstLatestActiveReservationByUserId(driverUserId).orElse(null);
    }

    private boolean isActiveReservationStatus(String status) {
        return status != null && ACTIVE_RESERVATION_STATUSES.contains(status.trim().toUpperCase());
    }

    private boolean isActiveSession(ParkingSession session) {
        return session.getSessionStatus() != null
                && ACTIVE_SESSION_STATUSES.contains(session.getSessionStatus().trim().toUpperCase());
    }

    private void validateSessionActiveForVerification(ParkingSession session) {
        if (!isActiveSession(session)) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Session is not active. Current status: " + session.getSessionStatus());
        }
    }

    private boolean isDriverOwnershipVerified(Incident incident, String sessionDriverEmail) {
        if (!"DRIVER".equals(incident.getReportSource())) {
            return true;
        }
        if (sessionDriverEmail == null || sessionDriverEmail.isBlank()) {
            return true;
        }
        return incident.getReporterId() != null
                && incident.getReporterId().equalsIgnoreCase(sessionDriverEmail);
    }

    private String buildVerificationFailureMessage(
            boolean plateMatch, boolean ticketMatch, boolean driverOwnershipVerified, boolean ticketProvided) {
        StringBuilder message = new StringBuilder("Vehicle ownership verification failed: ");
        if (!plateMatch) {
            message.append("Plate number does not match. ");
        }
        if (ticketProvided && !ticketMatch) {
            message.append("Ticket code does not match. ");
        }
        if (!driverOwnershipVerified) {
            message.append("Reporter does not match session driver account. ");
        }
        return message.toString().trim();
    }

    private LatestReservationResponse toLatestReservationResponse(Reservation r, ParkingSession session) {
        // Lay zone/floor/building tu reservation neu co, neu khong thi fallback qua session
        ParkingSlot effectiveSlot = r.getSlot() != null ? r.getSlot() : (session != null ? session.getSlot() : null);

        LatestReservationResponse.LatestReservationResponseBuilder b = LatestReservationResponse.builder()
                .reservationId(r.getReservationId())
                .reservationCode(r.getReservationCode())
                .reservationStatus(r.getReservationStatus())
                .reservationStart(r.getReservationStart())
                .createdAt(r.getCreatedAt());

        if (effectiveSlot != null) {
            ParkingSlot s = effectiveSlot;
            b.slotId(s.getSlotId()).slotName(s.getSlotName());
            if (s.getZone() != null) {
                b.zoneId(s.getZone().getZoneId()).zoneName(s.getZone().getZoneName());
                if (s.getZone().getFloor() != null) {
                    Floor f = s.getZone().getFloor();
                    b.floorId(f.getFloorId())
                            .floorName(f.getFloorName())
                            .floorLevel(f.getFloorLevel());
                    if (f.getBuilding() != null) {
                        b.buildingId(f.getBuilding().getBuildingId())
                                .buildingName(f.getBuilding().getBuildingName());
                    }
                }
            }
        }
        if (r.getVehicle() != null) {
            b.vehicleId(r.getVehicle().getVehicleId())
                    .vehiclePlate(r.getVehicle().getPlateNumber());
            if (r.getVehicle().getVehicleType() != null) {
                b.vehicleType(r.getVehicle().getVehicleType().getTypeName());
            }
        }
        if (r.getUser() != null) {
            b.driverUserId(r.getUser().getUserId())
                    .driverEmail(r.getUser().getEmail())
                    .driverFullName(r.getUser().getFullName());
        }
        // THEM: ticketCode tu session
        if (session != null && session.getTicket() != null) {
            b.ticketCode(session.getTicket().getTicketCode());
        }
        // THEM: session fees de staff thay gia tri hien tai cua session
        // Neu session chua checkout (totalFee = 0), fallback dung estimatedFee (gia uoc tinh luc reservation)
        if (session != null) {
            java.math.BigDecimal estimatedFee = session.getEstimatedFee() != null
                    ? session.getEstimatedFee() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalFee = session.getTotalFee() != null
                    ? session.getTotalFee() : java.math.BigDecimal.ZERO;
            String paymentStatus = session.getPaymentStatus() != null
                    ? session.getPaymentStatus() : "UNPAID";

            // Neu totalFee = 0 (chua checkout), hien thi estimatedFee cho sessionTotalFee
            // de staff thay gia tri uoc tinh, tranh hien "0 dong"
            java.math.BigDecimal displayTotalFee = totalFee.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? totalFee : estimatedFee;

            b.sessionEstimatedFee(estimatedFee)
                    .sessionTotalFee(displayTotalFee)
                    .sessionPaymentStatus(paymentStatus)
                    .estimatedFee(displayTotalFee);
        } else {
            b.estimatedFee(r.getEstimatedFee());
        }
        return b.build();
    }

    private AvailableSlotResponse toAvailableSlotResponse(ParkingSlot s, boolean hasActiveReservation, boolean availableForReassign) {
        AvailableSlotResponse.AvailableSlotResponseBuilder b = AvailableSlotResponse.builder()
                .slotId(s.getSlotId())
                .slotName(s.getSlotName())
                .slotStatus(s.getSlotStatus())
                .hasActiveReservation(hasActiveReservation)
                .available(availableForReassign)
                .inSameBuilding(availableForReassign)
                .message(availableForReassign ? "Slot is available for reassignment" : "Slot is not available for reassignment");
        if (s.getZone() != null) {
            b.zoneId(s.getZone().getZoneId()).zoneName(s.getZone().getZoneName());
            if (s.getZone().getFloor() != null) {
                b.floorId(s.getZone().getFloor().getFloorId())
                        .floorName(s.getZone().getFloor().getFloorName())
                        .floorLevel(s.getZone().getFloor().getFloorLevel());
                if (s.getZone().getFloor().getBuilding() != null) {
                    b.buildingId(s.getZone().getFloor().getBuilding().getBuildingId())
                            .buildingName(s.getZone().getFloor().getBuilding().getBuildingName());
                }
            }
        }
        return b.build();
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
                .verificationResult(incident.getVerificationResult())
                .verifiedAt(incident.getVerifiedAt())
                .verifiedBy(incident.getVerifiedBy())
                .build();
    }

    /**
     * Kiem tra staff co quyen truy cap incident nay khong.
     * Staff chi duoc phep xem/sua incident cua building ma ho duoc assign.
     * Admin/MANAGER duoc phep truy cap tat ca incident.
     *
     * @param staffEmail email cua staff
     * @param incident  incident can kiem tra
     * @throws BaseAPIException neu staff khong co quyen
     */
    private void validateStaffBuildingAccess(String staffEmail, Incident incident) {
        if (incident == null || incident.getSession() == null) {
            return;
        }

        java.util.Optional<String> incidentBuildingIdOpt = resolveIncidentBuildingId(incident);
        if (incidentBuildingIdOpt.isEmpty()) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Cannot determine building for this incident");
        }
        String incidentBuildingId = incidentBuildingIdOpt.get();

        List<String> staffBuildingIds = buildingStaffRepository.findBuildingIdsByUserId(
                userRepository.findByEmail(staffEmail)
                        .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffEmail))
                        .getUserId());

        if (!staffBuildingIds.contains(incidentBuildingId)) {
            throw new BaseAPIException(ErrorCode.FORBIDDEN,
                    "You do not have permission to access this incident. "
                            + "This incident belongs to a different building.");
        }
    }

    /**
     * Lay danh sach incident ma staff co quyen xem (thuoc building cua staff).
     *
     * @param staffEmail email cua staff
     * @return danh sach buildingId ma staff co quyen
     */
    private Set<String> getStaffAuthorizedBuildingIds(String staffEmail) {
        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffEmail));

        List<String> buildingIds = buildingStaffRepository.findBuildingIdsByUserId(staff.getUserId());
        return Set.copyOf(buildingIds);
    }

    private boolean isManagerOrAdmin(String email) {
        return userRepository.findByEmail(email)
                .map(User::getRole)
                .map(role -> "ROLE_MANAGER".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role))
                .orElse(false);
    }

    private void validateStaffCanAccessBuilding(String staffEmail, String buildingId) {
        if (isManagerOrAdmin(staffEmail)) {
            return;
        }
        Set<String> authorizedBuildingIds = getStaffAuthorizedBuildingIds(staffEmail);
        if (!authorizedBuildingIds.contains(buildingId)) {
            throw new BaseAPIException(ErrorCode.FORBIDDEN,
                    "You do not have permission to access incidents for this building.");
        }
    }

    /**
     * TOI UU: Kiem tra staff building access tu incident da fetch full chain.
     * Khong can them query vi buildingId da co trong incident.
     * Chi goi them query userRepository khi can staff info.
     */
    private void validateStaffBuildingAccessFromIncident(Incident incident, String staffEmail) {
        java.util.Optional<String> incidentBuildingIdOpt = resolveIncidentBuildingId(incident);
        if (incidentBuildingIdOpt.isEmpty()) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                    "Cannot determine building for this incident");
        }
        String incidentBuildingId = incidentBuildingIdOpt.get();

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffEmail));

        List<String> staffBuildingIds = buildingStaffRepository.findBuildingIdsByUserId(staff.getUserId());

        if (!staffBuildingIds.contains(incidentBuildingId)) {
            throw new BaseAPIException(ErrorCode.FORBIDDEN,
                    "You do not have permission to access this incident. "
                            + "This incident belongs to a different building.");
        }
    }
}
