package fpt.swp391.parkingmanagement.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import fpt.swp391.parkingmanagement.dto.CancelReservationRequest;
import fpt.swp391.parkingmanagement.dto.CreateReservationRequest;
import fpt.swp391.parkingmanagement.dto.ReservationResponse;
import fpt.swp391.parkingmanagement.dto.SlotAvailabilityDto;
import fpt.swp391.parkingmanagement.dto.SlotStatusResponse;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
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
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
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
import fpt.swp391.parkingmanagement.repository.ZoneSlotCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private static final Set<String> ACTIVE_BUILDING_FLOOR_STATUSES = Set.of("ACTIVE");
    private static final Set<String> ACTIVE_ZONE_STATUSES = Set.of("ACTIVE", "FULL");
    private static final Set<String> ACTIVE_RESERVATION_STATUSES = Set.of("PENDING", "CHECKED_IN");
    private static final Set<String> MANAGEABLE_RESERVATION_STATUSES = Set.of(
            "PENDING", "CHECKED_IN", "CANCELLED", "EXPIRED", "COMPLETED"
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
    private final ParkingSessionRepository parkingSessionRepository;

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
            PricingPolicyRepository pricingPolicyRepository,
            ParkingSessionRepository parkingSessionRepository) {
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
        this.parkingSessionRepository = parkingSessionRepository;
    }

    @Transactional(readOnly = true)
    public List<SlotAvailabilityDto> getAvailability(String buildingId, String vehicleTypeId) {
        // =====================================================================
        // Strategy:
        // 1. aggregateSlotCounts → counts per zone/floor (1 query)
        // 2. buildBuildingEnrichment → building data + vehicleTypes + pricing (1 query/building)
        // 3. findBySlotZoneIdIn → active reservations cho tất cả zones (1 query)
        // 4. Per-zone slot query → slots cho mỗi zone (1 query/zone, dùng index)
        // Total: ~4-5 queries cố định, MySQL dùng index hiệu quả.
        // =====================================================================

        // QUERY 1: Get zone stats with counts (1 query)
        List<ZoneSlotCount> zoneStats = parkingSlotRepository.aggregateSlotCounts(
                StringUtils.hasText(buildingId) ? normalizeText(buildingId) : null,
                StringUtils.hasText(vehicleTypeId) ? normalizeText(vehicleTypeId) : null);

        if (zoneStats.isEmpty()) {
            return List.of();
        }

        // Filter ACTIVE only
        zoneStats = zoneStats.stream()
                .filter(z -> "ACTIVE".equalsIgnoreCase(z.getBuildingStatus()))
                .filter(z -> "ACTIVE".equalsIgnoreCase(z.getFloorStatus()))
                .filter(z -> ACTIVE_ZONE_STATUSES.contains(normalize(z.getZoneStatus())))
                .toList();

        if (zoneStats.isEmpty()) {
            return List.of();
        }

        // QUERY 2: Load building enrichment cho tất cả buildings (1 query/building)
        Set<String> buildingIds = zoneStats.stream()
                .map(ZoneSlotCount::getBuildingId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, BuildingEnrichment> enrichments = new HashMap<>();
        for (String bId : buildingIds) {
            enrichments.put(bId, buildBuildingEnrichment(bId));
        }

        // QUERY 3: Batch load active reservations cho TẤT CẢ zones (1 query)
        List<String> zoneIds = zoneStats.stream().map(ZoneSlotCount::getZoneId).toList();
        List<Reservation> allReservations = reservationRepository
                .findBySlotZoneIdInAndReservationStatusInOrderByCreatedAtDesc(zoneIds, ACTIVE_RESERVATION_STATUSES);
        Map<String, Reservation> reservationBySlotId = allReservations.stream()
                .collect(Collectors.toMap(r -> r.getSlot().getSlotId(), r -> r, (a, b) -> a));

        // BUILD RESPONSE: per-zone slot query (dùng index → nhanh)
        List<SlotAvailabilityDto> result = new ArrayList<>();
        for (ZoneSlotCount zoneStat : zoneStats) {
            BuildingEnrichment enr = enrichments.get(zoneStat.getBuildingId());
            boolean isFirstForBuilding = enr != null && !enr.consumed;

            // QUERY 4 (per-zone): slots cho zone này (MySQL dùng index trên zone_id)
            List<ParkingSlot> zoneSlots = parkingSlotRepository
                    .findByZoneZoneIdOrderBySlotNameAsc(zoneStat.getZoneId());

            SlotAvailabilityDto.SlotAvailabilityDtoBuilder dtoBuilder = SlotAvailabilityDto.builder()
                    .buildingId(zoneStat.getBuildingId())
                    .buildingName(zoneStat.getBuildingName())
                    .floorId(zoneStat.getFloorId())
                    .floorName(zoneStat.getFloorName())
                    .floorLevel(zoneStat.getFloorLevel())
                    .floorStatus(zoneStat.getFloorStatus())
                    .floorVehicleTypeId(zoneStat.getVehicleTypeId())
                    .floorVehicleTypeName(zoneStat.getVehicleTypeName())
                    .zoneId(zoneStat.getZoneId())
                    .zoneName(zoneStat.getZoneName())
                    .zoneStatus(zoneStat.getZoneStatus())
                    .totalSlots(zoneStat.getTotalSlots() != null ? zoneStat.getTotalSlots().intValue() : 0)
                    .availableSlots(zoneStat.getAvailableSlots() != null ? zoneStat.getAvailableSlots().intValue() : 0);

            // Enrich building info 1 lần cho zone đầu tiên của building
            if (enr != null && isFirstForBuilding) {
                dtoBuilder.buildingAddress(enr.address)
                        .buildingPhone(enr.contactNumber)
                        .operatingStartTime(enr.operatingStartTime)
                        .operatingEndTime(enr.operatingEndTime)
                        .operatingHoursDisplay(enr.operatingHoursDisplay)
                        .parkingRules(enr.parkingRules)
                        .supportedVehicleTypes(enr.supportedVehicleTypes)
                        .pricingPolicies(enr.pricingPolicies);
                enr.consumed = true;
            }

            // Pre-load pricing cho vehicleType này (Caffeine cache → ko query DB thêm)
            PricingPolicy zonePolicy = pricingService.getActivePolicy(zoneStat.getVehicleTypeId());
            VehicleType vtRef = new VehicleType();
            vtRef.setVehicleTypeId(zoneStat.getVehicleTypeId());
            vtRef.setTypeName(zoneStat.getVehicleTypeName());

            List<SlotAvailabilityDto> slotDtos = new ArrayList<>(zoneSlots.size());
            for (ParkingSlot slot : zoneSlots) {
                slotDtos.add(buildSlotAvailabilityDto(slot, vtRef, zonePolicy,
                        reservationBySlotId.get(slot.getSlotId())));
            }
            dtoBuilder.slots(slotDtos);

            result.add(dtoBuilder.build());
        }
        return result;
    }

    /**
     * Holder cho data enrich của 1 building (tránh query lặp lại khi có nhiều zones).
     */
    private static class BuildingEnrichment {
        String address;
        String contactNumber;
        java.time.LocalTime operatingStartTime;
        java.time.LocalTime operatingEndTime;
        String operatingHoursDisplay;
        String parkingRules;
        List<VehicleTypeOptionResponse> supportedVehicleTypes;
        List<PricingPolicySummaryDto> pricingPolicies;
        boolean consumed = false;
    }

    /**
     * Build 1 lần cho cả building: vehicle types + pricing policies + operating hours.
     * Trước đây logic này nằm trong loop của getAvailability(), gọi lặp lại cho mỗi zone.
     */
    private BuildingEnrichment buildBuildingEnrichment(String buildingId) {
        BuildingEnrichment enr = new BuildingEnrichment();

        // QUERY: floors kèm vehicleType (1 query, dùng EntityGraph đã có)
        List<Floor> floors = floorRepository
                .findByBuildingBuildingIdOrderByFloorLevelAsc(buildingId).stream()
                .filter(f -> ACTIVE_BUILDING_FLOOR_STATUSES.contains(normalize(f.getStatus())))
                .toList();

        // Dedupe vehicleTypes theo vehicleTypeId
        enr.supportedVehicleTypes = floors.stream()
                .map(Floor::getVehicleType)
                .filter(vt -> vt != null)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                VehicleType::getVehicleTypeId,
                                vt -> VehicleTypeOptionResponse.builder()
                                        .vehicleTypeId(vt.getVehicleTypeId())
                                        .typeName(vt.getTypeName())
                                        .description(vt.getDescription())
                                        .sizeCategory(vt.getSizeCategory())
                                        .build(),
                                (a, b) -> a),
                        m -> new ArrayList<>(m.values())));

        // QUERY: pricing policies cho mỗi vehicle type (gọi 1 lần / vt, không trong loop zone)
        Map<String, PricingPolicySummaryDto> policyByVt = new LinkedHashMap<>();
        for (Floor f : floors) {
            if (f.getVehicleType() == null || f.getVehicleType().getVehicleTypeId() == null) continue;
            String vtId = f.getVehicleType().getVehicleTypeId();
            if (policyByVt.containsKey(vtId)) continue;
            pricingPolicyRepository.findAllActiveForVehicleType(vtId).stream()
                    .filter(p -> "ACTIVE".equals(p.getStatus()))
                    .findFirst()
                    .ifPresent(p -> policyByVt.put(vtId, PricingPolicySummaryDto.builder()
                            .policyId(p.getPolicyId())
                            .vehicleTypeId(p.getVehicleType().getVehicleTypeId())
                            .vehicleTypeName(p.getVehicleType().getTypeName())
                            .pricingType(p.getPricingType())
                            .basePrice(p.getBasePrice())
                            .hourlyRate(p.getHourlyRate())
                            .maxHours(p.getMaxHours())
                            .build()));
        }
        enr.pricingPolicies = new ArrayList<>(policyByVt.values());

        // Operating hours từ floor đầu tiên (cùng building nên giống nhau)
        if (!floors.isEmpty() && floors.get(0).getBuilding() != null) {
            Building b = floors.get(0).getBuilding();
            enr.address = b.getAddress();
            enr.contactNumber = b.getContactNumber();
            enr.operatingStartTime = b.getOperatingStartTime();
            enr.operatingEndTime = b.getOperatingEndTime();
            if (b.getOperatingStartTime() != null && b.getOperatingEndTime() != null) {
                enr.operatingHoursDisplay = b.getOperatingStartTime() + " - " + b.getOperatingEndTime();
            }
        }
        enr.parkingRules = "Vui lòng đặt trước chỗ đỗ xe. Xuất trình mã vé khi check-in. Giữ vé cẩn thận khi rời khỏi bãi đỗ.";

        return enr;
    }

    /**
     * Build SlotAvailabilityDto cho 1 slot, dùng data đã load sẵn (không query thêm).
     */
    private SlotAvailabilityDto buildSlotAvailabilityDto(ParkingSlot slot, VehicleType floorVehicleType,
                                                         PricingPolicy preloadedPolicy,
                                                         Reservation activeReservation) {
        SlotAvailabilityDto dto = new SlotAvailabilityDto();
        applyHierarchy(dto, slot, slot.getZone().getFloor());
        dto.setSlotStatus(slot.getSlotStatus());
        dto.setAvailableCount("AVAILABLE".equalsIgnoreCase(slot.getSlotStatus()) ? 1 : 0);
        dto.setVehicleTypeId(floorVehicleType.getVehicleTypeId());
        dto.setVehicleTypeName(floorVehicleType.getTypeName());

        // Dùng pricing đã pre-load cho zone (KHÔNG query lại)
        if (preloadedPolicy != null) {
            dto.setBasePrice(preloadedPolicy.getBasePrice());
            dto.setHourlyRate(preloadedPolicy.getHourlyRate());
            dto.setMaxHours(preloadedPolicy.getMaxHours());
        }

        // Reserved info lấy từ batch query đã load (KHÔNG query từng slot)
        if (activeReservation != null) {
            User user = activeReservation.getUser();
            Vehicle vehicle = activeReservation.getVehicle();
            dto.setReservedByUserId(user != null ? user.getUserId() : null);
            dto.setReservedByUsername(user != null ? user.getUsername() : null);
            dto.setReservedByVehicleId(vehicle != null ? vehicle.getVehicleId() : null);
        }

        return dto;
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

        List<Reservation> reservations = reservationRepository.findByUserUserIdOrderByCreatedAtDesc(user.getUserId());
        return batchToReservationResponse(reservations);
    }

    /**
     * FIX N+1: Convert list reservations sang response trong 2 query batch
     * thay vì 2 query / reservation.
     */
    private List<ReservationResponse> batchToReservationResponse(List<Reservation> reservations) {
        if (reservations.isEmpty()) return List.of();

        // Batch 1: load tất cả tickets
        List<String> reservationIds = reservations.stream().map(Reservation::getReservationId).toList();
        Map<String, Ticket> ticketByReservationId = ticketRepository
                .findByReservationReservationIdIn(reservationIds).stream()
                .collect(Collectors.toMap(t -> t.getReservation().getReservationId(), Function.identity(), (a, b) -> a));

        // Batch 2: load tất cả latest sessions (1 query thay vì N)
        Map<String, ParkingSession> latestSessionByResId = parkingSessionRepository
                .findLatestByReservationIds(reservationIds).stream()
                .collect(Collectors.toMap(s -> s.getReservation().getReservationId(), Function.identity(), (a, b) -> a));

        return reservations.stream()
                .map(r -> toReservationResponse(r,
                        ticketByReservationId.get(r.getReservationId()),
                        latestSessionByResId.get(r.getReservationId())))
                .toList();
    }

    // ============ STAFF APIs ============

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations(String staffEmail, String buildingId) {
        checkStaffBuildingAssignment(staffEmail, buildingId);
        return batchToReservationResponse(reservationRepository.findByBuildingBuildingIdOrderByCreatedAtDesc(buildingId));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservationsForStaff(String staffEmail) {
        String buildingId = getBuildingIdByStaffEmail(staffEmail);
        return batchToReservationResponse(reservationRepository.findByBuildingBuildingIdOrderByCreatedAtDesc(buildingId));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getPendingReservationsFifo(String staffEmail) {
        String buildingId = getBuildingIdByStaffEmail(staffEmail);
        return batchToReservationResponse(reservationRepository.findPendingByBuildingOrderByCreatedAtAsc(buildingId));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByStatusForStaff(String staffEmail, String status) {
        String buildingId = getBuildingIdByStaffEmail(staffEmail);
        return batchToReservationResponse(
                reservationRepository.findByBuildingBuildingIdAndReservationStatusOrderByCreatedAtDesc(buildingId, status));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByStatus(String staffEmail, String buildingId, String status) {
        checkStaffBuildingAssignment(staffEmail, buildingId);
        return batchToReservationResponse(
                reservationRepository.findByBuildingBuildingIdAndReservationStatusOrderByCreatedAtDesc(buildingId, status));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getPendingReservationsByBuilding(String staffEmail, String buildingId) {
        checkStaffBuildingAssignment(staffEmail, buildingId);
        return batchToReservationResponse(
                reservationRepository.findByBuildingBuildingIdAndReservationStatusOrderByCreatedAtDesc(buildingId, "PENDING"));
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

    /**
     * Driver cancel reservation của chính mình.
     * Chỉ cancel được khi status = PENDING (chưa checkin).
     * Staff cancel xem overloaded bên dưới.
     */
    @Transactional
    public ReservationResponse cancelReservationByDriver(String driverEmail, String reservationCode,
            CancelReservationRequest req) {
        User user = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));

        Reservation reservation = reservationRepository
                .findByReservationCodeFetchingDetails(normalizeText(reservationCode))
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationCode));

        // Validate: driver chỉ cancel được reservation của chính mình
        if (reservation.getUser() == null || !reservation.getUser().getUserId().equals(user.getUserId())) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED,
                    "Bạn không có quyền hủy reservation này");
        }

        // Validate: chỉ cancel được khi PENDING
        if (!"PENDING".equalsIgnoreCase(reservation.getReservationStatus())) {
            throw new BaseAPIException(ErrorCode.RESERVATION_NOT_APPROVED,
                    "Chỉ có thể hủy reservation khi đang ở trạng thái PENDING. "
                            + "Trạng thái hiện tại: " + reservation.getReservationStatus());
        }

        String reason = req != null && req.getReason() != null ? req.getReason() : "Driver cancelled";
        return doCancelReservation(reservation, reason);
    }

    /**
     * Staff cancel reservation giúp driver.
     * Staff có thể cancel khi status = PENDING hoặc CHECKED_IN.
     * Staff chỉ cancel được reservation thuộc building mình được assign.
     */
    @Transactional
    public ReservationResponse cancelReservationByStaff(String staffEmail, String reservationCode,
            CancelReservationRequest req) {
        String buildingId = getBuildingIdByStaffEmail(staffEmail);

        Reservation reservation = reservationRepository
                .findByBuildingBuildingIdAndReservationCodeFetchingDetails(buildingId, normalizeText(reservationCode))
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationCode));

        String currentStatus = reservation.getReservationStatus();
        if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
            throw new BaseAPIException(ErrorCode.RESERVATION_EXISTS_FOR_PLATE,
                    "Reservation đã bị hủy trước đó");
        }
        if ("COMPLETED".equalsIgnoreCase(currentStatus)) {
            throw new BaseAPIException(ErrorCode.RESERVATION_EXISTS_FOR_PLATE,
                    "Không thể hủy reservation đã hoàn thành");
        }
        if ("EXPIRED".equalsIgnoreCase(currentStatus)) {
            throw new BaseAPIException(ErrorCode.RESERVATION_EXISTS_FOR_PLATE,
                    "Không thể hủy reservation đã hết hạn");
        }

        // CHECKED_IN: phải checkout trước mới cancel được
        if ("CHECKED_IN".equalsIgnoreCase(currentStatus)) {
            throw new BaseAPIException(ErrorCode.RESERVATION_EXISTS_FOR_PLATE,
                    "Xe đã checkin. Vui lòng checkout trước khi hủy reservation.");
        }

        String reason = req != null && req.getReason() != null
                ? req.getReason()
                : "Staff cancelled (no reason provided)";

        return doCancelReservation(reservation, reason);
    }

    private ReservationResponse doCancelReservation(Reservation reservation, String reason) {
        String oldStatus = reservation.getReservationStatus();

        reservation.setReservationStatus("CANCELLED");
        reservation.setNote(reason);
        reservationRepository.save(reservation);

        // Giải phóng slot
        ParkingSlot slot = reservation.getSlot();
        if (slot != null) {
            slot.setSlotStatus("AVAILABLE");
            parkingSlotRepository.save(slot);
        }

        ReservationResponse response = toReservationResponse(reservation);

        // Gửi thông báo cho driver
        if (oldStatus != null && !oldStatus.equals("CANCELLED")) {
            sendStatusChangeNotification(reservation, oldStatus, "CANCELLED");
        }

        log.info("RESERVATION CANCELLED: code={}, reason={}", reservation.getReservationCode(), reason);
        return response;
    }

    @Transactional
    public ReservationResponse createReservation(String email, CreateReservationRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Vehicle vehicle = resolveVehicle(email, user, req);
        ParkingSlot slot = findSlot(req.getSlotId());
        validateSlotSelection(slot, vehicle);

        // Check if slot already has an active reservation
        var activeReservation = reservationRepository.findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc(
                slot.getSlotId(), ACTIVE_RESERVATION_STATUSES);
        if (activeReservation.isPresent()) {
            throw new RuntimeException("Selected slot already has an active reservation");
        }

        // 1 user không được đặt 2 reservation cùng loại xe cùng lúc
        String vehicleTypeName = vehicle.getVehicleType().getTypeName();
        validateNoActiveReservationByVehicleType(user.getUserId(), vehicleTypeName);

        slot.setSlotStatus("RESERVED");
        parkingSlotRepository.save(slot);

        Reservation reservation = new Reservation();
        reservation.setReservationCode("RS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        reservation.setReservationStart(req.getReservationStart());
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
        if ("APPROVED".equalsIgnoreCase(normalizedStatus) || "REJECTED".equalsIgnoreCase(normalizedStatus)) {
            throw new RuntimeException("Invalid reservation status. Approval flow was removed. Allowed values: " + String.join(", ", MANAGEABLE_RESERVATION_STATUSES));
        }
        reservation.setReservationStatus(normalizedStatus);
        reservation.setNote(normalizeText(note));

        ParkingSlot slot = reservation.getSlot();
        if (slot != null) {
            if ("CANCELLED".equals(normalizedStatus) || "EXPIRED".equals(normalizedStatus) || "COMPLETED".equals(normalizedStatus)) {
                slot.setSlotStatus("AVAILABLE");
                parkingSlotRepository.save(slot);
            }
        }

        Reservation saved = reservationRepository.save(reservation);
        Ticket ticket = ticketRepository.findByReservationReservationId(saved.getReservationId()).orElse(null);

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

        List<Reservation> expiredPending = reservationRepository.findExpiredPendingReservations(now);
        int expiredCount = 0;
        for (Reservation reservation : expiredPending) {
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

        return expiredCount;
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

    private void validateNoActiveReservationByVehicleType(String userId, String vehicleTypeName) {
        List<Reservation> active = reservationRepository.findActiveReservationsByVehicleType(
                userId, vehicleTypeName, ACTIVE_RESERVATION_STATUSES);
        if (!active.isEmpty()) {
            throw new RuntimeException("You already have an active reservation for " + vehicleTypeName);
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

    /**
     * Overload dùng cho batch convert - ticket + session đã được load sẵn,
     * tránh N+1 query.
     */
    private ReservationResponse toReservationResponse(Reservation reservation, Ticket ticket, ParkingSession session) {
        ReservationResponse resp = toReservationResponse(reservation, ticket);
        // Pricing
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
        // Session (đã load sẵn)
        if (session != null) {
            resp.setSessionId(session.getSessionId());
            resp.setCheckinTime(session.getCheckinTime());
            resp.setCheckoutTime(session.getCheckoutTime());
            resp.setTotalFee(session.getTotalFee());
            resp.setCheckinImageUrl(session.getCheckinImageUrl());
            resp.setCheckoutImageUrl(session.getCheckoutImageUrl());
            resp.setParkingDuration(session.getParkingDuration());
            resp.setPaymentStatus(session.getPaymentStatus());
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
        resp.setCreatedAt(reservation.getCreatedAt());

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

        parkingSessionRepository
                .findFirstByReservationReservationIdOrderByCreatedAtDesc(reservation.getReservationId())
                .ifPresent(session -> {
                    resp.setSessionId(session.getSessionId());
                    resp.setCheckinTime(session.getCheckinTime());
                    resp.setCheckoutTime(session.getCheckoutTime());
                    resp.setTotalFee(session.getTotalFee());
                    resp.setCheckinImageUrl(session.getCheckinImageUrl());
                    resp.setCheckoutImageUrl(session.getCheckoutImageUrl());
                    resp.setParkingDuration(session.getParkingDuration());
                    resp.setPaymentStatus(session.getPaymentStatus());
                });

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
