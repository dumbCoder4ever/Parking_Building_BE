package fpt.swp391.parkingmanagement.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import fpt.swp391.parkingmanagement.dto.CreateReservationRequest;
import fpt.swp391.parkingmanagement.dto.ReservationResponse;
import fpt.swp391.parkingmanagement.dto.SlotAvailabilityDto;
import fpt.swp391.parkingmanagement.dto.SlotStatusResponse;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.Ticket;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.repository.VehicleRepository;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import fpt.swp391.parkingmanagement.repository.ZoneRepository;
import fpt.swp391.parkingmanagement.repository.BuildingStaffRepository;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import fpt.swp391.parkingmanagement.service.PricingService;
import fpt.swp391.parkingmanagement.dto.PricingPolicySummaryDto;
import fpt.swp391.parkingmanagement.dto.VehicleTypeOptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private static final Set<String> ACTIVE_BUILDING_FLOOR_STATUSES = Set.of("ACTIVE");
    private static final Set<String> ACTIVE_ZONE_STATUSES = Set.of("ACTIVE", "FULL");
    private static final Set<String> ACTIVE_RESERVATION_STATUSES = Set.of("PENDING", "APPROVED");
    private static final Set<String> MANAGEABLE_RESERVATION_STATUSES = Set.of(
            "PENDING", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED", 
            "COMPLETED", "NO_SHOW", "CHECKED_IN", "PENDING_PAYMENT"
    );

    private final ParkingSlotRepository parkingSlotRepository;
    private final BuildingRepository buildingRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final VehicleService vehicleService;
    private final NotificationService notificationService;
    private final BuildingStaffRepository buildingStaffRepository;
    private final PricingService pricingService;
    private final PricingPolicyRepository pricingPolicyRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public ReservationService(
            ParkingSlotRepository parkingSlotRepository,
            BuildingRepository buildingRepository,
            VehicleRepository vehicleRepository,
            ReservationRepository reservationRepository,
            TicketRepository ticketRepository,
            VehicleTypeRepository vehicleTypeRepository,
            FloorRepository floorRepository,
            ZoneRepository zoneRepository,
            UserRepository userRepository,
            VehicleService vehicleService,
            NotificationService notificationService,
            BuildingStaffRepository buildingStaffRepository,
            PricingService pricingService,
            PricingPolicyRepository pricingPolicyRepository) {
        this.parkingSlotRepository = parkingSlotRepository;
        this.buildingRepository = buildingRepository;
        this.vehicleRepository = vehicleRepository;
        this.reservationRepository = reservationRepository;
        this.ticketRepository = ticketRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.floorRepository = floorRepository;
        this.zoneRepository = zoneRepository;
        this.userRepository = userRepository;
        this.vehicleService = vehicleService;
        this.notificationService = notificationService;
        this.buildingStaffRepository = buildingStaffRepository;
        this.pricingService = pricingService;
        this.pricingPolicyRepository = pricingPolicyRepository;
    }

    @Transactional(readOnly = true)
    public List<SlotAvailabilityDto> getAvailability(String buildingId, String vehicleTypeId) {
        List<SlotAvailabilityDto> result = new ArrayList<>();

        List<Floor> floors = resolveFloors(buildingId);
        Set<String> processedBuildings = new java.util.HashSet<>();

        for (Floor floor : floors) {
            if (!ACTIVE_BUILDING_FLOOR_STATUSES.contains(normalize(floor.getStatus()))) {
                continue;
            }
            VehicleType floorVehicleType = floor.getVehicleType();
            if (floorVehicleType == null || floorVehicleType.getVehicleTypeId() == null) {
                continue;
            }
            if (StringUtils.hasText(vehicleTypeId)
                    && !normalizeText(vehicleTypeId).equals(floorVehicleType.getVehicleTypeId())) {
                continue;
            }

            Building building = floor.getBuilding();
            if (building == null) {
                continue;
            }

            // Check if this is the first floor for this building
            boolean isFirstFloorForBuilding = processedBuildings.add(building.getBuildingId());

            // Get building-level info only for the first floor
            List<VehicleTypeOptionResponse> supportedVehicleTypes = null;
            List<PricingPolicySummaryDto> pricingPolicies = null;
            String operatingHoursDisplay = null;
            String parkingRules = null;

            if (isFirstFloorForBuilding) {
                // Get all vehicle types supported by this building's floors
                List<VehicleTypeOptionResponse> vehicleTypes = floorRepository
                        .findByBuildingBuildingIdOrderByFloorLevelAsc(building.getBuildingId()).stream()
                        .filter(f -> ACTIVE_BUILDING_FLOOR_STATUSES.contains(normalize(f.getStatus())))
                        .map(Floor::getVehicleType)
                        .filter(vt -> vt != null)
                        .distinct()
                        .map(vt -> VehicleTypeOptionResponse.builder()
                                .vehicleTypeId(vt.getVehicleTypeId())
                                .typeName(vt.getTypeName())
                                .description(vt.getDescription())
                                .sizeCategory(vt.getSizeCategory())
                                .build())
                        .toList();

                // Get active pricing policies for each vehicle type
                List<PricingPolicySummaryDto> policies = new ArrayList<>();
                for (Floor f : floorRepository.findByBuildingBuildingIdOrderByFloorLevelAsc(building.getBuildingId())) {
                    if (f.getVehicleType() != null && f.getVehicleType().getVehicleTypeId() != null) {
                        pricingPolicyRepository.findAllActiveForVehicleType(f.getVehicleType().getVehicleTypeId())
                                .stream()
                                .filter(p -> p.getStatus().equals("ACTIVE"))
                                .findFirst()
                                .ifPresent(p -> {
                                    policies.add(PricingPolicySummaryDto.builder()
                                            .policyId(p.getPolicyId())
                                            .vehicleTypeId(p.getVehicleType().getVehicleTypeId())
                                            .vehicleTypeName(p.getVehicleType().getTypeName())
                                            .pricingType(p.getPricingType())
                                            .basePrice(p.getBasePrice())
                                            .hourlyRate(p.getHourlyRate())
                                            .maxHours(p.getMaxHours())
                                            .build());
                                });
                    }
                }

                // Build operating hours display
                if (building.getOperatingStartTime() != null && building.getOperatingEndTime() != null) {
                    operatingHoursDisplay = String.format("%s - %s",
                            building.getOperatingStartTime().toString(),
                            building.getOperatingEndTime().toString());
                }

                // Default parking rules only if building has no custom rules
                parkingRules = "Vui lòng đặt trước chỗ đỗ xe. Xuất trình mã vé khi check-in. Giữ vé cẩn thận khi rời khỏi bãi đỗ.";

                supportedVehicleTypes = vehicleTypes;
                pricingPolicies = policies;
            }

            // Only show parking rules if available
            String effectiveParkingRules = (parkingRules != null && !parkingRules.isBlank()) ? parkingRules : null;

            List<Zone> zones = zoneRepository.findByFloorFloorIdOrderByZoneNameAsc(floor.getFloorId());
            for (Zone zone : zones) {
                if (!ACTIVE_ZONE_STATUSES.contains(normalize(zone.getStatus()))) {
                    continue;
                }

                List<ParkingSlot> slots = parkingSlotRepository.findByZoneZoneIdOrderBySlotNameAsc(zone.getZoneId());

                List<SlotAvailabilityDto> slotDtos = slots.stream()
                        .map(slot -> toSlotAvailability(slot, floorVehicleType))
                        .toList();

                long availableSlots = slotDtos.stream()
                        .filter(slot -> "AVAILABLE".equalsIgnoreCase(slot.getSlotStatus()))
                        .count();

                // Build zone DTO - ALWAYS include building basic info
                SlotAvailabilityDto.SlotAvailabilityDtoBuilder dtoBuilder = SlotAvailabilityDto.builder()
                        .buildingId(building.getBuildingId())
                        .buildingName(building.getBuildingName())
                        .floorId(floor.getFloorId())
                        .floorName(floor.getFloorName())
                        .floorLevel(floor.getFloorLevel())
                        .floorStatus(floor.getStatus())
                        .floorVehicleTypeId(floorVehicleType.getVehicleTypeId())
                        .floorVehicleTypeName(floorVehicleType.getTypeName())
                        .zoneId(zone.getZoneId())
                        .zoneName(zone.getZoneName())
                        .zoneStatus(zone.getStatus())
                        .totalSlots(slotDtos.size())
                        .availableSlots(availableSlots)
                        .slots(slotDtos);

                // Only add enriched building info on first floor to avoid duplication
                if (isFirstFloorForBuilding) {
                    dtoBuilder.buildingAddress(building.getAddress())
                            .buildingPhone(building.getContactNumber())
                            .operatingStartTime(building.getOperatingStartTime())
                            .operatingEndTime(building.getOperatingEndTime())
                            .operatingHoursDisplay(operatingHoursDisplay)
                            .parkingRules(effectiveParkingRules)
                            .supportedVehicleTypes(supportedVehicleTypes)
                            .pricingPolicies(pricingPolicies);
                }

                result.add(dtoBuilder.build());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SlotStatusResponse getSlotStatus(String slotId) {
        ParkingSlot slot = findSlot(slotId);
        Reservation reservation = reservationRepository.findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc(
                        slot.getSlotId(), ACTIVE_RESERVATION_STATUSES)
                .orElse(null);
        Ticket ticket = reservation == null ? null : ticketRepository.findByReservationReservationId(reservation.getReservationId()).orElse(null);
        return toSlotStatus(slot, reservation, ticket);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return reservationRepository.findByUserUserIdOrderByCreatedAtDesc(user.getUserId()).stream()
                .map(this::toReservationResponse)
                .toList();
    }

    // ============ STAFF APIs ============

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations(String staffEmail, String buildingId) {
        checkStaffBuildingAssignment(staffEmail, buildingId);
        return reservationRepository.findByBuildingBuildingIdOrderByCreatedAtDesc(buildingId).stream()
                .map(this::toReservationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservationsForStaff(String staffEmail) {
        String buildingId = getBuildingIdByStaffEmail(staffEmail);
        return reservationRepository.findByBuildingBuildingIdOrderByCreatedAtDesc(buildingId).stream()
                .map(this::toReservationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByStatusForStaff(String staffEmail, String status) {
        String buildingId = getBuildingIdByStaffEmail(staffEmail);
        return reservationRepository.findByBuildingBuildingIdAndReservationStatusOrderByCreatedAtDesc(buildingId, status).stream()
                .map(this::toReservationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByStatus(String staffEmail, String buildingId, String status) {
        checkStaffBuildingAssignment(staffEmail, buildingId);
        return reservationRepository.findByBuildingBuildingIdAndReservationStatusOrderByCreatedAtDesc(buildingId, status).stream()
                .map(this::toReservationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(String staffEmail, String buildingId, String reservationId) {
        checkStaffBuildingAssignment(staffEmail, buildingId);
        Reservation reservation = reservationRepository.findByBuildingBuildingIdAndReservationId(buildingId, reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        return toReservationResponse(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationByCode(String staffEmail, String buildingId, String reservationCode) {
        checkStaffBuildingAssignment(staffEmail, buildingId);
        Reservation reservation = reservationRepository
                .findByBuildingBuildingIdAndReservationCodeFetchingDetails(buildingId, reservationCode)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationCode));
        return toReservationResponse(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationByCodeForStaff(String staffEmail, String reservationCode) {
        String buildingId = getBuildingIdByStaffEmail(staffEmail);
        Reservation reservation = reservationRepository
                .findByReservationCodeFetchingDetails(reservationCode)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationCode));
        if (reservation.getSlot() != null && reservation.getSlot().getZone() != null
                && reservation.getSlot().getZone().getFloor() != null
                && !buildingId.equals(reservation.getSlot().getZone().getFloor().getBuilding().getBuildingId())) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED, "Reservation does not belong to your building");
        }
        return toReservationResponse(reservation);
    }

    @Transactional
    public ReservationResponse updateReservationStatusForStaff(String staffEmail, String reservationCode,
            String status, String note) {
        String buildingId = getBuildingIdByStaffEmail(staffEmail);
        return updateReservationStatus(staffEmail, buildingId, reservationCode, status, note);
    }

    @Transactional
    public ReservationResponse createReservation(String email, CreateReservationRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateReservationWindow(req);
        Vehicle vehicle = resolveVehicle(email, user, req);
        ParkingSlot slot = findSlot(req.getSlotId());
        validateSlotSelection(slot, vehicle);

        // Check if slot already has an active reservation
        var activeReservation = reservationRepository.findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc(
                slot.getSlotId(), ACTIVE_RESERVATION_STATUSES);
        if (activeReservation.isPresent()) {
            throw new RuntimeException("Selected slot already has an active reservation");
        }

        // 1 user có thể đặt 1 CAR và 1 BIKE cùng ngày, miễn không trùng giờ cùng loại xe
        String vehicleTypeName = vehicle.getVehicleType().getTypeName();
        validateNoTimeConflictByVehicleType(user.getUserId(), vehicleTypeName, req.getReservationStart(), req.getReservationEnd());

        slot.setSlotStatus("RESERVED");
        parkingSlotRepository.save(slot);

        Reservation reservation = new Reservation();
        reservation.setReservationCode("RS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        reservation.setReservationStart(req.getReservationStart());
        reservation.setReservationEnd(req.getReservationEnd());
        reservation.setSlot(slot);
        reservation.setUser(user);
        reservation.setVehicle(vehicle);
        reservation.setReservationStatus("PENDING");
        reservation = reservationRepository.save(reservation);

        Ticket ticket = new Ticket();
        ticket.setReservation(reservation);
        ticket.setTicketCode("T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ticket = ticketRepository.save(ticket);

        ReservationResponse response = toReservationResponse(reservation, ticket);

        sendReservationNotification(reservation, "RESERVATION_CREATED", "Reservation created successfully");

        return response;
    }

    private void sendReservationNotification(Reservation reservation, String event, String message) {
        if (reservation.getUser() != null) {
            User driver = reservation.getUser();
            ReservationResponse payload = toReservationResponse(reservation);
            notificationService.sendToUser(driver.getUsername(), event, payload);
            log.info("NOTIFICATION TO {}: {}", driver.getUsername(), message);
        }
    }

    @Transactional
    public ReservationResponse updateReservationStatus(
            String staffEmail, String buildingId, String reservationCode, String status, String note) {
        checkStaffBuildingAssignment(staffEmail, buildingId);
        Reservation reservation = reservationRepository
                .findByBuildingBuildingIdAndReservationCodeFetchingDetails(buildingId, normalizeText(reservationCode))
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationCode));

        String oldStatus = reservation.getReservationStatus();
        String normalizedStatus = validateReservationStatus(status);
        reservation.setReservationStatus(normalizedStatus);
        reservation.setNote(normalizeText(note));

        ParkingSlot slot = reservation.getSlot();
        if (slot != null) {
            if ("APPROVED".equals(normalizedStatus)) {
                slot.setSlotStatus("RESERVED");
            } else if ("REJECTED".equals(normalizedStatus) || "CANCELLED".equals(normalizedStatus)) {
                slot.setSlotStatus("AVAILABLE");
            } else if ("COMPLETED".equals(normalizedStatus) || "EXPIRED".equals(normalizedStatus)) {
                slot.setSlotStatus("AVAILABLE");
            }
            parkingSlotRepository.save(slot);
        }

        Reservation saved = reservationRepository.save(reservation);
        Ticket ticket = ticketRepository.findByReservationReservationId(saved.getReservationId()).orElse(null);

        // Set ticket expiredAt when reservation is approved
        if (ticket != null && "APPROVED".equals(normalizedStatus)) {
            Integer gracePeriodMinutes = saved.getGracePeriodMinutes();
            int grace = gracePeriodMinutes != null ? gracePeriodMinutes : 15;
            if (saved.getReservationEnd() != null) {
                ticket.setExpiredAt(saved.getReservationEnd().plusMinutes(grace));
                ticketRepository.save(ticket);
            }
        }

        ReservationResponse response = toReservationResponse(saved, ticket);

        if (oldStatus != null && !oldStatus.equals(normalizedStatus)) {
            sendStatusChangeNotification(reservation, oldStatus, normalizedStatus);
        }

        return response;
    }

    private void sendStatusChangeNotification(Reservation reservation, String oldStatus, String newStatus) {
        if (reservation.getUser() != null) {
            User driver = reservation.getUser();
            String message;
            switch (newStatus) {
                case "APPROVED":
                    message = "Your reservation " + reservation.getReservationCode() + " has been APPROVED. Please arrive on time.";
                    break;
                case "REJECTED":
                    message = "Your reservation " + reservation.getReservationCode() + " has been REJECTED. Reason: " + (reservation.getNote() != null ? reservation.getNote() : "N/A");
                    break;
                case "CANCELLED":
                    message = "Your reservation " + reservation.getReservationCode() + " has been CANCELLED.";
                    break;
                case "COMPLETED":
                    message = "Your reservation " + reservation.getReservationCode() + " has been COMPLETED. Thank you for using our service.";
                    break;
                case "EXPIRED":
                    message = "Your reservation " + reservation.getReservationCode() + " has EXPIRED. You did not check-in before the grace period.";
                    break;
                default:
                    message = "Your reservation status changed from " + oldStatus + " to " + newStatus;
            }
            ReservationResponse payload = toReservationResponse(reservation);
            notificationService.sendToUser(driver.getUsername(), "RESERVATION_STATUS_CHANGED", payload);
            log.info("NOTIFICATION TO {}: {}", driver.getUsername(), message);
        }
    }

    @Transactional
    public int autoExpireReservations() {
        LocalDateTime now = LocalDateTime.now();

        // PENDING reservations -> CANCELLED (staff không approve kịp)
        List<Reservation> expiredPending = reservationRepository.findExpiredPendingReservations(now);
        int cancelledCount = 0;
        for (Reservation reservation : expiredPending) {
            ParkingSlot slot = reservation.getSlot();
            if (slot != null) {
                reservation.setReservationStatus("CANCELLED");
                reservation.setNote("Auto-cancelled: staff did not approve before grace period");
                reservationRepository.save(reservation);

                slot.setSlotStatus("AVAILABLE");
                parkingSlotRepository.save(slot);

                sendAutoExpireNotification(reservation, "CANCELLED");
                cancelledCount++;
            }
        }

        // APPROVED reservations -> EXPIRED (driver không check-in kịp)
        List<Reservation> expiredApproved = reservationRepository.findExpiredApprovedReservations(now);
        int expiredCount = 0;
        for (Reservation reservation : expiredApproved) {
            ParkingSlot slot = reservation.getSlot();
            if (slot != null) {
                reservation.setReservationStatus("EXPIRED");
                reservation.setNote("Auto-expired: driver did not check-in before grace period");
                reservationRepository.save(reservation);

                slot.setSlotStatus("AVAILABLE");
                parkingSlotRepository.save(slot);

                sendAutoExpireNotification(reservation, "EXPIRED");
                expiredCount++;
            }
        }

        return cancelledCount + expiredCount;
    }

    private void sendAutoExpireNotification(Reservation reservation, String newStatus) {
        if (reservation.getUser() != null) {
            User driver = reservation.getUser();
            String message;
            if ("EXPIRED".equals(newStatus)) {
                message = "Your reservation " + reservation.getReservationCode() + " has EXPIRED. You did not check-in before the grace period. Please book again.";
            } else {
                message = "Your reservation " + reservation.getReservationCode() + " has been CANCELLED. Please book again.";
            }
            ReservationResponse payload = toReservationResponse(reservation);
            notificationService.sendToUser(driver.getUsername(), "RESERVATION_" + newStatus, payload);
            log.info("NOTIFICATION TO {}: {}", driver.getUsername(), message);
        }
    }

    private List<Floor> resolveFloors(String buildingId) {
        if (StringUtils.hasText(buildingId)) {
            return floorRepository.findByBuildingBuildingIdOrderByFloorLevelAsc(normalizeText(buildingId));
        }
        return floorRepository.findByBuildingStatusIgnoreCaseOrderByBuildingBuildingNameAscFloorLevelAsc("ACTIVE");
    }

    private ParkingSlot findSlot(String slotId) {
        return parkingSlotRepository.findBySlotId(normalizeText(slotId))
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + slotId));
    }

    private void validateReservationWindow(CreateReservationRequest req) {
        if (!req.getReservationEnd().isAfter(req.getReservationStart())) {
            throw new RuntimeException("Reservation end must be after reservation start");
        }
        
        long hours = java.time.Duration.between(req.getReservationStart(), req.getReservationEnd()).toHours();
        if (hours > 24) {
            throw new RuntimeException("Reservation duration cannot exceed 24 hours");
        }
    }

    private void validateNoTimeConflictByVehicleType(String userId, String vehicleTypeName, LocalDateTime start, LocalDateTime end) {
        // 1 user có thể đặt 1 CAR và 1 BIKE cùng ngày, miễn không trùng giờ cùng loại xe
        List<Reservation> overlapping = reservationRepository.findOverlappingReservationsByVehicleType(
                userId, vehicleTypeName, start, end, ACTIVE_RESERVATION_STATUSES);
        if (!overlapping.isEmpty()) {
            throw new RuntimeException("You already have a reservation for " + vehicleTypeName + " that overlaps with this time slot");
        }
    }

    private void validateSlotSelection(ParkingSlot slot, Vehicle vehicle) {
        Zone zone = slot.getZone();
        if (zone == null || zone.getFloor() == null || zone.getFloor().getBuilding() == null) {
            throw new RuntimeException("Selected slot is not fully configured");
        }
        if (!"AVAILABLE".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new RuntimeException("Selected slot is not available");
        }

        Floor floor = zone.getFloor();
        if (!ACTIVE_BUILDING_FLOOR_STATUSES.contains(normalize(floor.getStatus()))) {
            throw new RuntimeException("Selected floor is not active");
        }
        if (!ACTIVE_ZONE_STATUSES.contains(normalize(zone.getStatus()))) {
            throw new RuntimeException("Selected zone is not active");
        }

        VehicleType floorVehicleType = floor.getVehicleType();
        VehicleType vehicleType = vehicle.getVehicleType();
        if (floorVehicleType == null || vehicleType == null
                || !floorVehicleType.getVehicleTypeId().equals(vehicleType.getVehicleTypeId())) {
            throw new RuntimeException("Selected slot does not support this vehicle type");
        }
    }

    private Vehicle resolveVehicle(String email, User user, CreateReservationRequest req) {
        if (!StringUtils.hasText(req.getPlateNumber())) {
            throw new RuntimeException("Plate number is required");
        }
        if (!StringUtils.hasText(req.getVehicleTypeId())) {
            throw new RuntimeException("Vehicle type id is required");
        }

        VehicleType vt = vehicleTypeRepository.findById(normalizeText(req.getVehicleTypeId()))
                .orElseThrow(() -> new RuntimeException("Vehicle type not found"));

        return vehicleRepository.findByPlateNumberIgnoreCase(req.getPlateNumber())
                .map(existing -> {
                    if (existing.getUser() != null && !existing.getUser().getUserId().equals(user.getUserId())) {
                        throw new RuntimeException("Plate number already registered by another user");
                    }
                    existing.setVehicleColor(req.getVehicleColor());
                    existing.setBrand(req.getBrand());
                    existing.setModel(req.getModel());
                    return vehicleRepository.save(existing);
                })
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setPlateNumber(req.getPlateNumber().trim().toUpperCase());
                    v.setVehicleColor(req.getVehicleColor());
                    v.setBrand(req.getBrand());
                    v.setModel(req.getModel());
                    v.setVehicleType(vt);
                    v.setUser(user);
                    v.setStatus("ACTIVE");
                    return vehicleRepository.save(v);
                });
    }

    private SlotAvailabilityDto toSlotAvailability(ParkingSlot slot, VehicleType floorVehicleType) {
        SlotAvailabilityDto dto = new SlotAvailabilityDto();
        applyHierarchy(dto, slot, slot.getZone().getFloor());
        dto.setSlotStatus(slot.getSlotStatus());
        dto.setAvailableCount("AVAILABLE".equalsIgnoreCase(slot.getSlotStatus()) ? 1 : 0);
        dto.setVehicleTypeId(floorVehicleType.getVehicleTypeId());
        dto.setVehicleTypeName(floorVehicleType.getTypeName());
        
        var policy = pricingService.getActivePolicy(floorVehicleType.getVehicleTypeId());
        if (policy != null) {
            dto.setBasePrice(policy.getBasePrice());
            dto.setHourlyRate(policy.getHourlyRate());
            dto.setMaxHours(policy.getMaxHours());
        }

        // Set reserved user info nếu slot đang RESERVED
        if ("RESERVED".equalsIgnoreCase(slot.getSlotStatus())) {
            var reservation = reservationRepository.findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc(
                    slot.getSlotId(), ACTIVE_RESERVATION_STATUSES).orElse(null);
            if (reservation != null) {
                User user = reservation.getUser();
                Vehicle vehicle = reservation.getVehicle();
                dto.setReservedByUserId(user != null ? user.getUserId() : null);
                dto.setReservedByUsername(user != null ? user.getUsername() : null);
                dto.setReservedByVehicleId(vehicle != null ? vehicle.getVehicleId() : null);
            }
        }

        return dto;
    }

    private ReservationResponse toReservationResponse(Reservation reservation) {
        Ticket ticket = ticketRepository.findByReservationReservationId(reservation.getReservationId()).orElse(null);
        ReservationResponse resp = toReservationResponse(reservation, ticket);

        // Attach pricing if vehicle exists
        if (reservation.getVehicle() != null && reservation.getVehicle().getVehicleType() != null) {
            String vtId = reservation.getVehicle().getVehicleType().getVehicleTypeId();
            resp.setVehicleTypeName(reservation.getVehicle().getVehicleType().getTypeName());
            var policy = pricingService.getActivePolicy(vtId);
            if (policy != null) {
                resp.setBasePrice(policy.getBasePrice());
                resp.setHourlyRate(policy.getHourlyRate());
                resp.setMaxHours(policy.getMaxHours());
            }
        }

        return resp;
    }

    private ReservationResponse toReservationResponse(Reservation reservation, Ticket ticket) {
        ReservationResponse resp = new ReservationResponse();
        resp.setReservationId(reservation.getReservationId());
        resp.setReservationCode(reservation.getReservationCode());
        resp.setReservationStatus(reservation.getReservationStatus());
        resp.setReservationNote(reservation.getNote());
        resp.setReservationStart(reservation.getReservationStart());
        resp.setReservationEnd(reservation.getReservationEnd());

        // User info
        if (reservation.getUser() != null) {
            resp.setUserId(reservation.getUser().getUserId());
            resp.setUsername(reservation.getUser().getUsername());
        }

        // Vehicle info
        Vehicle vehicle = reservation.getVehicle();
        if (vehicle != null) {
            resp.setVehicleId(vehicle.getVehicleId());
            resp.setVehiclePlate(vehicle.getPlateNumber());
            resp.setVehicleColor(vehicle.getVehicleColor());
            resp.setVehicleBrand(vehicle.getBrand());
            resp.setVehicleModel(vehicle.getModel());

            // Pricing by vehicle type
            if (vehicle.getVehicleType() != null) {
                String vtId = vehicle.getVehicleType().getVehicleTypeId();
                resp.setVehicleTypeName(vehicle.getVehicleType().getTypeName());
                var policy = pricingService.getActivePolicy(vtId);
                if (policy != null) {
                    resp.setBasePrice(policy.getBasePrice());
                    resp.setHourlyRate(policy.getHourlyRate());
                    resp.setMaxHours(policy.getMaxHours());
                }
            }
        }

        ParkingSlot slot = reservation.getSlot();
        if (slot != null) {
            applyHierarchy(resp, slot);
            resp.setSlotStatus(slot.getSlotStatus());
        }

        if (ticket != null) {
            resp.setTicketCode(ticket.getTicketCode());
        }
        return resp;
    }

    private SlotStatusResponse toSlotStatus(ParkingSlot slot, Reservation reservation, Ticket ticket) {
        Zone zone = slot.getZone();
        Floor floor = zone != null ? zone.getFloor() : null;
        Building building = floor != null ? floor.getBuilding() : null;

        return SlotStatusResponse.builder()
                .slotId(slot.getSlotId())
                .slotName(slot.getSlotName())
                .slotStatus(slot.getSlotStatus())
                .buildingId(building != null ? building.getBuildingId() : null)
                .buildingName(building != null ? building.getBuildingName() : null)
                .floorId(floor != null ? floor.getFloorId() : null)
                .floorName(floor != null ? floor.getFloorName() : null)
                .floorLevel(floor != null ? floor.getFloorLevel() : null)
                .zoneId(zone != null ? zone.getZoneId() : null)
                .zoneName(zone != null ? zone.getZoneName() : null)
                .zoneStatus(zone != null ? zone.getStatus() : null)
                .reservationId(reservation != null ? reservation.getReservationId() : null)
                .reservationCode(reservation != null ? reservation.getReservationCode() : null)
                .reservationStatus(reservation != null ? reservation.getReservationStatus() : null)
                .reservationStart(reservation != null ? reservation.getReservationStart() : null)
                .reservationEnd(reservation != null ? reservation.getReservationEnd() : null)
                .ticketCode(ticket != null ? ticket.getTicketCode() : null)
                .ticketUsed(ticket != null ? ticket.getIsUsed() : null)
                .vehicleId(reservation != null && reservation.getVehicle() != null ? reservation.getVehicle().getVehicleId() : null)
                .vehiclePlateNumber(reservation != null && reservation.getVehicle() != null ? reservation.getVehicle().getPlateNumber() : null)
                .driverId(reservation != null && reservation.getUser() != null ? reservation.getUser().getUserId() : null)
                .driverUsername(reservation != null && reservation.getUser() != null ? reservation.getUser().getUsername() : null)
                .build();
    }

    private String validateReservationStatus(String status) {
        String normalized = normalize(status);
        if (!MANAGEABLE_RESERVATION_STATUSES.contains(normalized)) {
            throw new RuntimeException("Invalid reservation status. Allowed values: " + String.join(", ", MANAGEABLE_RESERVATION_STATUSES));
        }
        return normalized;
    }

    private void applyHierarchy(SlotAvailabilityDto dto, ParkingSlot slot, Floor floor) {
        Floor resolvedFloor = floor;
        Zone zone = null;
        Building building = null;

        if (slot != null) {
            dto.setSlotId(slot.getSlotId());
            dto.setSlotName(slot.getSlotName());
            zone = slot.getZone();
        }

        if (zone != null) {
            dto.setZoneId(zone.getZoneId());
            dto.setZoneName(zone.getZoneName());
            dto.setZoneStatus(zone.getStatus());
            if (resolvedFloor == null) {
                resolvedFloor = zone.getFloor();
            }
        }

        if (resolvedFloor != null) {
            dto.setFloorId(resolvedFloor.getFloorId());
            dto.setFloorName(resolvedFloor.getFloorName());
            dto.setFloorLevel(resolvedFloor.getFloorLevel());
            dto.setFloorStatus(resolvedFloor.getStatus());
            building = resolvedFloor.getBuilding();
            VehicleType vt = resolvedFloor.getVehicleType();
            if (vt != null) {
                dto.setFloorVehicleTypeId(vt.getVehicleTypeId());
                dto.setFloorVehicleTypeName(vt.getTypeName());
            }
        }

        if (building != null) {
            dto.setBuildingId(building.getBuildingId());
            dto.setBuildingName(building.getBuildingName());
        }
    }

    private void applyHierarchy(ReservationResponse resp, ParkingSlot slot) {
        resp.setSlotId(slot.getSlotId());
        resp.setSlotName(slot.getSlotName());

        Zone zone = slot.getZone();
        if (zone != null) {
            resp.setZoneId(zone.getZoneId());
            resp.setZoneName(zone.getZoneName());
            resp.setZoneStatus(zone.getStatus());

            Floor floor = zone.getFloor();
            if (floor != null) {
                resp.setFloorId(floor.getFloorId());
                resp.setFloorName(floor.getFloorName());
                resp.setFloorLevel(floor.getFloorLevel());

                VehicleType vehicleType = floor.getVehicleType();
                if (vehicleType != null) {
                    resp.setFloorVehicleTypeId(vehicleType.getVehicleTypeId());
                    resp.setFloorVehicleTypeName(vehicleType.getTypeName());
                }

                Building building = floor.getBuilding();
                if (building != null) {
                    resp.setBuildingId(building.getBuildingId());
                    resp.setBuildingName(building.getBuildingName());
                }
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private void checkStaffBuildingAssignment(String email, String buildingId) {
        if (!buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId(buildingId, getUserIdByEmail(email))) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED,
                    "You are not assigned to this building");
        }
    }

    private String getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String getBuildingIdByStaffEmail(String email) {
        String userId = getUserIdByEmail(email);
        List<String> buildingIds = buildingStaffRepository.findBuildingIdsByUserId(userId);
        if (buildingIds.isEmpty()) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED, "Staff is not assigned to any building");
        }
        if (buildingIds.size() > 1) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED,
                    "Staff is assigned to multiple buildings. Please specify a building.");
        }
        return buildingIds.get(0);
    }
}
