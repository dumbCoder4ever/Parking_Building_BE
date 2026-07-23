package fpt.swp391.parkingmanagement.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.CreateBuildingRequest;
import fpt.swp391.parkingmanagement.dto.CreateFloorRequest;
import fpt.swp391.parkingmanagement.dto.CreateZoneRequest;
import fpt.swp391.parkingmanagement.dto.ManagerSetupResponse;
import fpt.swp391.parkingmanagement.dto.SlotOccupancyDetailResponse;
import fpt.swp391.parkingmanagement.dto.UpdateBuildingRequest;
import fpt.swp391.parkingmanagement.dto.UpdateFloorRequest;
import fpt.swp391.parkingmanagement.dto.UpdateZoneRequest;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.Ticket;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.DuplicateResourceException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingFloorStats;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import fpt.swp391.parkingmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ManagerBuildingSetupService {

    private static final Set<String> BUILDING_FLOOR_STATUSES = Set.of("ACTIVE", "INACTIVE", "MAINTENANCE");
    private static final Set<String> ZONE_STATUSES = Set.of("ACTIVE", "INACTIVE", "FULL", "MAINTENANCE");
    private static final Set<String> SLOT_MANAGEABLE_STATUSES = Set.of("AVAILABLE", "MAINTENANCE");
    private static final Set<String> SLOT_BLOCKED_FOR_MAINTENANCE = Set.of("RESERVED", "OCCUPIED", "PENDING_EXIT");
    private static final Set<String> ACTIVE_RESERVATION_STATUSES = Set.of("PENDING", "APPROVED", "CHECKED_IN");

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;
    private final ZoneStatusSyncService zoneStatusSyncService;

    @Cacheable(value = "managerBuildings", key = "'all'")
    @Transactional(readOnly = true)
    public List<ManagerSetupResponse> getAllBuildings() {
        List<Building> buildings = buildingRepository.findAll();
        if (buildings.isEmpty()) {
            return List.of();
        }

        Map<String, BuildingFloorStats> floorStats = floorRepository.aggregateStatsByBuilding().stream()
                .collect(Collectors.toMap(BuildingFloorStats::getBuildingId, s -> s, (a, b) -> a));
        Map<String, Long> zoneCounts = zoneRepository.countGroupedByBuilding().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a));
        Map<String, Long> slotCounts = parkingSlotRepository.countGroupedByBuilding().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a));

        return buildings.stream()
                .map(b -> toBuildingSummary(b, floorStats.get(b.getBuildingId()),
                        zoneCounts.getOrDefault(b.getBuildingId(), 0L),
                        slotCounts.getOrDefault(b.getBuildingId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagerSetupResponse getBuilding(String buildingId) {
        Building building = findBuilding(buildingId);
        return toBuildingDetail(building);
    }

    @Cacheable(value = "managerFloors", key = "#buildingId")
    @Transactional(readOnly = true)
    public List<ManagerSetupResponse> getFloorsByBuilding(String buildingId) {
        List<Floor> floors = floorRepository.findByBuildingBuildingIdOrderByFloorLevelAsc(buildingId);
        if (floors.isEmpty() && !buildingRepository.existsById(buildingId)) {
            throw new ResourceNotFoundException("Building not found: " + buildingId);
        }
        Map<String, Long> zoneCountByFloor = toCountMap(
                zoneRepository.countGroupedByFloorForBuilding(buildingId));
        return floors.stream()
                .map(floor -> toFloorResponse(floor, zoneCountByFloor.getOrDefault(floor.getFloorId(), 0L)))
                .toList();
    }

    @Cacheable(value = "managerZones", key = "#floorId")
    @Transactional(readOnly = true)
    public List<ManagerSetupResponse> getZonesByFloor(String floorId) {
        Floor floor = findFloor(floorId);
        List<Zone> zones = zoneRepository.findByFloorFloorIdOrderByZoneNameAsc(floor.getFloorId());
        Map<String, Long> slotCountByZone = toCountMap(
                parkingSlotRepository.countGroupedByZoneForFloor(floor.getFloorId()));
        return zones.stream()
                .map(zone -> toZoneResponse(zone, slotCountByZone.getOrDefault(zone.getZoneId(), 0L)))
                .toList();
    }

    @Cacheable(value = "managerSlots", key = "#zoneId")
    @Transactional(readOnly = true)
    public List<ManagerSetupResponse> getSlotsByZone(String zoneId) {
        List<ParkingSlot> slots = parkingSlotRepository.findByZoneZoneIdOrderBySlotNameAsc(zoneId);
        if (slots.isEmpty() && !zoneRepository.existsById(zoneId)) {
            throw new ResourceNotFoundException("Zone not found: " + zoneId);
        }
        return slots.stream()
                .sorted(slotByIndexAscending())
                .map(this::toSlotResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SlotOccupancyDetailResponse getSlotOccupancyDetail(String slotId) {
        ParkingSlot slot = findSlot(slotId);
        String slotStatus = slot.getSlotStatus();

        if ("AVAILABLE".equalsIgnoreCase(slotStatus) || "MAINTENANCE".equalsIgnoreCase(slotStatus)) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST, "Slot is not occupied or reserved");
        }

        ParkingSession session = parkingSessionRepository.findCurrentBySlotId(slot.getSlotId()).orElse(null);
        Reservation reservation = session != null && session.getReservation() != null
                ? session.getReservation()
                : reservationRepository
                        .findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc(
                                slot.getSlotId(), ACTIVE_RESERVATION_STATUSES)
                        .orElse(null);

        if (reservation == null && session == null) {
            throw new BaseAPIException(
                    ErrorCode.INVALID_REQUEST,
                    "No active reservation or parking session found for this slot");
        }

        Ticket ticket = reservation != null
                ? ticketRepository.findByReservationReservationId(reservation.getReservationId()).orElse(null)
                : (session != null ? session.getTicket() : null);

        return toSlotOccupancyDetail(slot, reservation, session, ticket);
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true),
            @CacheEvict(value = "managerSlots", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse updateSlotStatus(String slotId, String status) {
        ParkingSlot slot = findSlot(slotId);
        String normalized = validateSlotStatus(status);
        String current = slot.getSlotStatus() == null ? "" : slot.getSlotStatus().trim().toUpperCase();

        if ("MAINTENANCE".equals(normalized) && SLOT_BLOCKED_FOR_MAINTENANCE.contains(current)) {
            throw new BaseAPIException(
                    ErrorCode.INVALID_REQUEST,
                    "Cannot set MAINTENANCE while slot is " + current);
        }
        if (!SLOT_MANAGEABLE_STATUSES.contains(current) && !"MAINTENANCE".equals(current)
                && "AVAILABLE".equals(normalized)) {
            throw new BaseAPIException(
                    ErrorCode.INVALID_REQUEST,
                    "Use force-reset for slots in " + current + " status");
        }

        ParkingSlot saved = zoneStatusSyncService.updateSlotStatus(slot, normalized);
        String buildingId = resolveBuildingId(saved);
        auditLogService.record(
                "SLOT_STATUS_UPDATE",
                "PARKING_SLOT",
                saved.getSlotId(),
                buildingId,
                current,
                normalized,
                "Slot status updated",
                null);
        return toSlotResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true),
            @CacheEvict(value = "managerSlots", allEntries = true)
    })
    @Transactional
    public void forceResetSlotStatus(String slotId) {
        ParkingSlot slot = findSlot(slotId);
        String currentStatus = slot.getSlotStatus();
        if ("AVAILABLE".equalsIgnoreCase(currentStatus)) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST, "Slot is already AVAILABLE");
        }
        zoneStatusSyncService.updateSlotStatus(slot, "AVAILABLE");
        auditLogService.record(
                "SLOT_FORCE_RESET",
                "PARKING_SLOT",
                slot.getSlotId(),
                resolveBuildingId(slot),
                currentStatus,
                "AVAILABLE",
                "Slot force-reset to AVAILABLE",
                null);
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse createBuilding(CreateBuildingRequest request) {
        validateBuildingTimes(request.getOperatingStartTime(), request.getOperatingEndTime());

        Building building = new Building();
        building.setBuildingName(normalizeText(request.getBuildingName()));
        building.setAddress(normalizeText(request.getAddress()));
        building.setTotalFloors(request.getTotalFloors());
        building.setOperatingStartTime(request.getOperatingStartTime());
        building.setOperatingEndTime(request.getOperatingEndTime());
        building.setContactNumber(normalizeText(request.getContactNumber()));
        building.setStatus("ACTIVE");

        Building saved = buildingRepository.save(building);
        return toBuildingDetail(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse updateBuilding(String buildingId, UpdateBuildingRequest request) {
        Building building = findBuilding(buildingId);
        validateBuildingTimes(request.getOperatingStartTime(), request.getOperatingEndTime());

        long currentFloorCount = floorRepository.countByBuildingBuildingId(buildingId);
        if (request.getTotalFloors() < currentFloorCount) {
            throw new RuntimeException("Total floors cannot be less than the number of existing floors");
        }

        building.setBuildingName(normalizeText(request.getBuildingName()));
        building.setAddress(normalizeText(request.getAddress()));
        building.setTotalFloors(request.getTotalFloors());
        building.setOperatingStartTime(request.getOperatingStartTime());
        building.setOperatingEndTime(request.getOperatingEndTime());
        building.setContactNumber(normalizeText(request.getContactNumber()));

        return toBuildingDetail(buildingRepository.save(building));
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true),
            @CacheEvict(value = "managerFloors", allEntries = true),
            @CacheEvict(value = "managerZones", allEntries = true),
            @CacheEvict(value = "managerSlots", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse updateBuildingStatus(String buildingId, String status) {
        Building building = findBuilding(buildingId);
        String oldStatus = building.getStatus();
        String normalized = validateBuildingOrFloorStatus(status);
        building.setStatus(normalized);
        Building saved = buildingRepository.save(building);
        if ("MAINTENANCE".equals(normalized)) {
            cascadeMaintenanceFromBuilding(buildingId);
        } else if ("INACTIVE".equals(normalized)) {
            cascadeInactiveFromBuilding(buildingId);
        } else if ("ACTIVE".equals(normalized)) {
            cascadeActiveFromBuilding(buildingId);
        }
        auditLogService.record(
                "BUILDING_STATUS_UPDATE",
                "BUILDING",
                buildingId,
                buildingId,
                oldStatus,
                normalized,
                "Building status updated",
                null);
        return toBuildingDetail(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true),
            @CacheEvict(value = "managerFloors", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse createFloor(String buildingId, CreateFloorRequest request) {
        Building building = findBuilding(buildingId);
        VehicleType vehicleType = resolveVehicleType(buildingId, request);

        validateFloorRequest(building, request, null);

        if (floorRepository.existsByBuildingBuildingIdAndVehicleTypeVehicleTypeId(
                building.getBuildingId(), vehicleType.getVehicleTypeId())) {
            throw new DuplicateResourceException("This building already has a floor for this vehicle type");
        }

        Floor floor = new Floor();
        floor.setBuilding(building);
        floor.setVehicleType(vehicleType);
        floor.setFloorName(normalizeText(request.getFloorName()));
        floor.setFloorLevel(request.getFloorLevel());
        floor.setMaxCapacity(request.getMaxCapacity());
        floor.setCurrentOccupancy(0);
        floor.setStatus("ACTIVE");

        return toFloorResponse(floorRepository.save(floor));
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true),
            @CacheEvict(value = "managerFloors", allEntries = true),
            @CacheEvict(value = "managerZones", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse updateFloor(String floorId, UpdateFloorRequest request) {
        Floor floor = findFloor(floorId);
        String buildingId = floor.getBuilding().getBuildingId();
        String normalizedName = normalizeText(request.getFloorName());
        VehicleType vehicleType = resolveVehicleTypeId(buildingId, request.getVehicleTypeId());

        if (floorRepository.existsByBuildingBuildingIdAndFloorNameIgnoreCaseAndFloorIdNot(
                buildingId, normalizedName, floorId)) {
            throw new DuplicateResourceException("Floor name already exists in this building");
        }

        if (floorRepository.existsByBuildingBuildingIdAndVehicleTypeVehicleTypeIdAndFloorIdNot(
                buildingId, vehicleType.getVehicleTypeId(), floorId)) {
            throw new DuplicateResourceException("This building already has a floor for this vehicle type");
        }

        int usedZoneCapacity = zoneRepository.findByFloorFloorId(floorId).stream()
                .map(Zone::getMaxCapacity)
                .filter(value -> value != null && value > 0)
                .reduce(0, Integer::sum);
        if (request.getMaxCapacity() < usedZoneCapacity) {
            throw new RuntimeException("Floor max capacity cannot be less than total zone capacity");
        }

        floor.setFloorName(normalizedName);
        floor.setVehicleType(vehicleType);
        floor.setMaxCapacity(request.getMaxCapacity());
        return toFloorResponse(floorRepository.save(floor));
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true),
            @CacheEvict(value = "managerFloors", allEntries = true),
            @CacheEvict(value = "managerZones", allEntries = true),
            @CacheEvict(value = "managerSlots", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse updateFloorStatus(String floorId, String status) {
        Floor floor = findFloor(floorId);
        String oldStatus = floor.getStatus();
        String normalized = validateBuildingOrFloorStatus(status);
        String buildingId = floor.getBuilding() != null ? floor.getBuilding().getBuildingId() : null;
        floor.setStatus(normalized);
        floorRepository.save(floor);
        if ("MAINTENANCE".equals(normalized)) {
            cascadeMaintenanceFromFloor(floorId);
        } else if ("INACTIVE".equals(normalized)) {
            cascadeInactiveFromFloor(floorId);
        } else if ("ACTIVE".equals(normalized)) {
            cascadeActiveFromFloor(floorId);
        }
        // Reload after bulk UPDATE clears persistence context
        Floor saved = floorRepository.findById(floorId).orElse(floor);
        auditLogService.record(
                "FLOOR_STATUS_UPDATE",
                "FLOOR",
                floorId,
                buildingId,
                oldStatus,
                normalized,
                "Floor status updated",
                null);
        return toFloorResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true),
            @CacheEvict(value = "managerFloors", allEntries = true),
            @CacheEvict(value = "managerZones", allEntries = true),
            @CacheEvict(value = "managerSlots", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse createZoneAndSlots(String floorId, CreateZoneRequest request) {
        Floor floor = findFloor(floorId);
        validateZoneRequest(floor, request);

        Zone zone = new Zone();
        zone.setFloor(floor);
        zone.setZoneName(normalizeText(request.getZoneName()));
        zone.setMaxCapacity(request.getMaxCapacity());
        zone.setCurrentOccupancy(0);
        zone.setStatus("ACTIVE");
        Zone savedZone = zoneRepository.save(zone);

        List<ParkingSlot> slots = buildSlots(savedZone, slotPrefixFromZone(savedZone), request.getMaxCapacity());
        parkingSlotRepository.saveAll(slots);

        ManagerSetupResponse response = toZoneResponse(savedZone);
        response.setCreatedSlots(slots.size());
        return response;
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true),
            @CacheEvict(value = "managerZones", allEntries = true),
            @CacheEvict(value = "managerSlots", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse updateZone(String zoneId, UpdateZoneRequest request) {
        Zone zone = findZone(zoneId);
        Floor floor = zone.getFloor();
        String normalizedName = normalizeText(request.getZoneName());

        if (zoneRepository.existsByFloorFloorIdAndZoneNameIgnoreCaseAndZoneIdNot(
                floor.getFloorId(), normalizedName, zoneId)) {
            throw new DuplicateResourceException("Zone name already exists on this floor");
        }

        List<ParkingSlot> existingSlots =
                parkingSlotRepository.findByZoneZoneIdOrderBySlotNameAsc(zone.getZoneId());
        int currentSlotCount = existingSlots.size();
        int targetSlotCount = request.getMaxCapacity();

        int usedCapacityExcludingZone = zoneRepository.findByFloorFloorId(floor.getFloorId()).stream()
                .filter(otherZone -> !otherZone.getZoneId().equals(zoneId))
                .map(Zone::getMaxCapacity)
                .filter(value -> value != null && value > 0)
                .reduce(0, Integer::sum);
        int nextTotalCapacity = usedCapacityExcludingZone + targetSlotCount;
        if (floor.getMaxCapacity() != null && nextTotalCapacity > floor.getMaxCapacity()) {
            throw new RuntimeException("Total zone capacity exceeds floor max capacity");
        }

        if (targetSlotCount < currentSlotCount) {
            long nonRemovableSlots = existingSlots.stream()
                    .filter(slot -> !"AVAILABLE".equalsIgnoreCase(slot.getSlotStatus()))
                    .count();
            if (targetSlotCount < nonRemovableSlots) {
                throw new RuntimeException(
                        "Cannot reduce slots below the number of reserved or occupied slots");
            }
            removeAvailableSlots(existingSlots, currentSlotCount - targetSlotCount);
        } else if (targetSlotCount > currentSlotCount) {
            String slotPrefix = existingSlots.isEmpty()
                    ? slotPrefixFromZone(zone)
                    : deriveSlotPrefix(existingSlots);
            List<ParkingSlot> newSlots = buildSlots(
                    zone, slotPrefix, currentSlotCount + 1, targetSlotCount);
            parkingSlotRepository.saveAll(newSlots);
        }

        zone.setZoneName(normalizedName);
        zone.setMaxCapacity(targetSlotCount);
        Zone savedZone = zoneRepository.save(zone);
        zoneStatusSyncService.syncZone(savedZone.getZoneId());
        ManagerSetupResponse response = toZoneResponse(savedZone);
        int addedSlots = targetSlotCount - currentSlotCount;
        if (addedSlots > 0) {
            response.setCreatedSlots(addedSlots);
        }
        return response;
    }

    @Caching(evict = {
            @CacheEvict(value = "managerBuildings", allEntries = true),
            @CacheEvict(value = "managerFloors", allEntries = true),
            @CacheEvict(value = "managerZones", allEntries = true),
            @CacheEvict(value = "managerSlots", allEntries = true)
    })
    @Transactional
    public ManagerSetupResponse updateZoneStatus(String zoneId, String status) {
        Zone zone = findZone(zoneId);
        String oldStatus = zone.getStatus();
        String normalized = validateZoneStatus(status);
        String buildingId = null;
        if (zone.getFloor() != null && zone.getFloor().getBuilding() != null) {
            buildingId = zone.getFloor().getBuilding().getBuildingId();
        }
        zone.setStatus(normalized);
        zoneRepository.save(zone);
        if ("MAINTENANCE".equals(normalized)) {
            parkingSlotRepository.bulkAvailableToMaintenanceByZoneId(zoneId);
        } else if ("ACTIVE".equals(normalized) || "FULL".equals(normalized)) {
            parkingSlotRepository.bulkMaintenanceToAvailableByZoneId(zoneId);
            zoneStatusSyncService.syncZone(zoneId);
        }
        Zone saved = zoneRepository.findByZoneId(zoneId).orElse(zone);
        auditLogService.record(
                "ZONE_STATUS_UPDATE",
                "ZONE",
                zoneId,
                buildingId,
                oldStatus,
                normalized,
                "Zone status updated",
                null);
        return toZoneResponse(saved);
    }

    /** Building → Floor → Zone → Slot via bulk UPDATE (no entity load). */
    private void cascadeMaintenanceFromBuilding(String buildingId) {
        floorRepository.bulkUpdateStatusByBuildingId(buildingId, "MAINTENANCE");
        zoneRepository.bulkUpdateStatusByBuildingId(buildingId, "MAINTENANCE");
        parkingSlotRepository.bulkAvailableToMaintenanceByBuildingId(buildingId);
    }

    /** Floor → Zone → Slot via bulk UPDATE. */
    private void cascadeMaintenanceFromFloor(String floorId) {
        zoneRepository.bulkUpdateStatusByFloorId(floorId, "MAINTENANCE");
        parkingSlotRepository.bulkAvailableToMaintenanceByFloorId(floorId);
    }

    /** Building → Floor → Zone only. Slots unchanged. */
    private void cascadeInactiveFromBuilding(String buildingId) {
        floorRepository.bulkUpdateStatusByBuildingId(buildingId, "INACTIVE");
        zoneRepository.bulkUpdateStatusByBuildingId(buildingId, "INACTIVE");
    }

    /** Floor → Zone only. Slots unchanged. */
    private void cascadeInactiveFromFloor(String floorId) {
        zoneRepository.bulkUpdateStatusByFloorId(floorId, "INACTIVE");
    }

    /** Restore closed children + sync zone ACTIVE/FULL in a few bulk UPDATEs. */
    private void cascadeActiveFromBuilding(String buildingId) {
        parkingSlotRepository.bulkMaintenanceToAvailableByBuildingId(buildingId);
        floorRepository.bulkReopenClosedByBuildingId(buildingId);
        zoneRepository.bulkReopenClosedByBuildingId(buildingId);
        zoneRepository.bulkMarkFullWhenNoAvailableByBuildingId(buildingId);
    }

    private void cascadeActiveFromFloor(String floorId) {
        parkingSlotRepository.bulkMaintenanceToAvailableByFloorId(floorId);
        zoneRepository.bulkReopenClosedByFloorId(floorId);
        zoneRepository.bulkMarkFullWhenNoAvailableByFloorId(floorId);
    }

    private Building findBuilding(String buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found: " + buildingId));
    }

    private Floor findFloor(String floorId) {
        return floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found: " + floorId));
    }

    private Zone findZone(String zoneId) {
        return zoneRepository.findByZoneId(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found: " + zoneId));
    }

    private ParkingSlot findSlot(String slotId) {
        return parkingSlotRepository.findBySlotId(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + slotId));
    }

    private VehicleType resolveVehicleType(String buildingId, CreateFloorRequest request) {
        return resolveVehicleTypeId(buildingId, request.getVehicleTypeId());
    }

    private VehicleType resolveVehicleTypeId(String buildingId, String vehicleTypeIdRaw) {
        String vehicleTypeId = normalizeText(vehicleTypeIdRaw);

        if (vehicleTypeId.equals(buildingId)) {
            throw new RuntimeException(
                    "vehicleTypeId must not be the buildingId from the URL. "
                            + "Call GET /api/vehicles/types and copy vehicleTypeId from the response.");
        }

        return vehicleTypeRepository.findById(vehicleTypeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle type not found for id: " + vehicleTypeId
                                + ". Call GET /api/vehicles/types. "
                                + "Example motorbike id: " + CreateFloorRequest.MOTORBIKE_TYPE_ID));
    }

    private void validateBuildingTimes(java.time.LocalTime start, java.time.LocalTime end) {
        if (!end.isAfter(start)) {
            throw new RuntimeException("Operating end time must be after operating start time");
        }
    }

    private void validateFloorRequest(Building building, CreateFloorRequest request, String excludeFloorId) {
        if (request.getFloorLevel() > building.getTotalFloors()) {
            throw new RuntimeException("Floor level exceeds building total floors");
        }

        String normalizedName = normalizeText(request.getFloorName());
        boolean levelExists = excludeFloorId == null
                ? floorRepository.existsByBuildingBuildingIdAndFloorLevel(building.getBuildingId(), request.getFloorLevel())
                : floorRepository.existsByBuildingBuildingIdAndFloorLevelAndFloorIdNot(
                        building.getBuildingId(), request.getFloorLevel(), excludeFloorId);
        if (levelExists) {
            throw new DuplicateResourceException("Floor level already exists in this building");
        }

        boolean nameExists = excludeFloorId == null
                ? floorRepository.existsByBuildingBuildingIdAndFloorNameIgnoreCase(building.getBuildingId(), normalizedName)
                : floorRepository.existsByBuildingBuildingIdAndFloorNameIgnoreCaseAndFloorIdNot(
                        building.getBuildingId(), normalizedName, excludeFloorId);
        if (nameExists) {
            throw new DuplicateResourceException("Floor name already exists in this building");
        }

        long currentFloorCount = floorRepository.countByBuildingBuildingId(building.getBuildingId());
        if (currentFloorCount >= building.getTotalFloors()) {
            throw new RuntimeException("This building already has enough floors as configured");
        }
    }

    private void validateZoneRequest(Floor floor, CreateZoneRequest request) {
        if (zoneRepository.existsByFloorFloorIdAndZoneNameIgnoreCase(floor.getFloorId(), normalizeText(request.getZoneName()))) {
            throw new DuplicateResourceException("Zone name already exists on this floor");
        }

        int usedCapacity = zoneRepository.findByFloorFloorId(floor.getFloorId()).stream()
                .map(Zone::getMaxCapacity)
                .filter(value -> value != null && value > 0)
                .reduce(0, Integer::sum);

        int nextCapacity = usedCapacity + request.getMaxCapacity();
        if (floor.getMaxCapacity() != null && nextCapacity > floor.getMaxCapacity()) {
            throw new RuntimeException("Total zone capacity exceeds floor max capacity");
        }
    }

    private List<ParkingSlot> buildSlots(Zone zone, String slotPrefix, int count) {
        return buildSlots(zone, slotPrefix, 1, count);
    }

    private List<ParkingSlot> buildSlots(Zone zone, String slotPrefix, int startIndex, int endIndex) {
        List<ParkingSlot> slots = new ArrayList<>(endIndex - startIndex + 1);
        for (int i = startIndex; i <= endIndex; i++) {
            String slotName = slotPrefix + "-" + i;
            if (parkingSlotRepository.existsByZoneZoneIdAndSlotNameIgnoreCase(zone.getZoneId(), slotName)) {
                throw new DuplicateResourceException("Slot name duplicated in zone: " + slotName);
            }
            ParkingSlot slot = new ParkingSlot();
            slot.setZone(zone);
            slot.setSlotName(slotName);
            slot.setSlotStatus("AVAILABLE");
            slots.add(slot);
        }
        return slots;
    }

    private void removeAvailableSlots(List<ParkingSlot> slots, int slotsToRemove) {
        List<ParkingSlot> toDelete = slots.stream()
                .filter(slot -> "AVAILABLE".equalsIgnoreCase(slot.getSlotStatus()))
                .sorted(slotByIndexDescending())
                .limit(slotsToRemove)
                .toList();

        if (toDelete.size() < slotsToRemove) {
            throw new RuntimeException("Not enough available slots to remove");
        }

        parkingSlotRepository.deleteAll(toDelete);
    }

    private String deriveSlotPrefix(List<ParkingSlot> slots) {
        if (slots.isEmpty()) {
            throw new RuntimeException("Cannot derive slot prefix from an empty slot list");
        }

        String slotName = slots.get(0).getSlotName();
        int lastDash = slotName.lastIndexOf('-');
        if (lastDash > 0) {
            return slotName.substring(0, lastDash);
        }
        return slotName;
    }

    private String slotPrefixFromZone(Zone zone) {
        String name = normalizeText(zone.getZoneName());
        if (name == null || name.isBlank()) {
            return "Slot";
        }
        String prefix = name.replaceAll("\\s+", "-").replaceAll("[^\\p{L}\\p{N}-]", "");
        return prefix.isBlank() ? "Slot" : prefix;
    }

    private String validateBuildingOrFloorStatus(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase();
        if (normalized == null || !BUILDING_FLOOR_STATUSES.contains(normalized)) {
            throw new RuntimeException("Invalid status. Allowed values: ACTIVE, INACTIVE, MAINTENANCE");
        }
        return normalized;
    }

    private String validateZoneStatus(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase();
        if (normalized == null || !ZONE_STATUSES.contains(normalized)) {
            throw new RuntimeException("Invalid status. Allowed values: ACTIVE, INACTIVE, FULL, MAINTENANCE");
        }
        return normalized;
    }

    private String validateSlotStatus(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase();
        if (normalized == null || !SLOT_MANAGEABLE_STATUSES.contains(normalized)) {
            throw new BaseAPIException(
                    ErrorCode.INVALID_REQUEST,
                    "Invalid slot status. Allowed values: AVAILABLE, MAINTENANCE");
        }
        return normalized;
    }

    private String resolveBuildingId(ParkingSlot slot) {
        if (slot == null || slot.getZone() == null || slot.getZone().getFloor() == null
                || slot.getZone().getFloor().getBuilding() == null) {
            return null;
        }
        return slot.getZone().getFloor().getBuilding().getBuildingId();
    }

    private ManagerSetupResponse toBuildingSummary(Building building) {
        String buildingId = building.getBuildingId();
        BuildingFloorStats floorStats = floorRepository.aggregateStatsForBuilding(buildingId).orElse(null);
        long zoneCount = zoneRepository.countByFloorBuildingBuildingId(buildingId);
        long slotCount = parkingSlotRepository.countByBuildingId(buildingId);
        return toBuildingSummary(building, floorStats, zoneCount, slotCount);
    }

    private ManagerSetupResponse toBuildingSummary(
            Building building,
            BuildingFloorStats floorStats,
            long zoneCount,
            long slotCount) {
        long floorCount = floorStats != null ? floorStats.getFloorCount() : 0L;
        int maxCapacity = floorStats != null ? floorStats.getMaxCapacity() : 0;
        int currentOccupancy = floorStats != null ? floorStats.getCurrentOccupancy() : 0;
        return toBuildingSummary(building, floorCount, zoneCount, slotCount, maxCapacity, currentOccupancy);
    }

    private ManagerSetupResponse toBuildingSummary(
            Building building,
            long floorCount,
            long zoneCount,
            long slotCount,
            int maxCapacity,
            int currentOccupancy) {
        String buildingId = building.getBuildingId();
        return ManagerSetupResponse.builder()
                .id(buildingId)
                .name(building.getBuildingName())
                .type("BUILDING")
                .address(building.getAddress())
                .contactNumber(building.getContactNumber())
                .totalFloors(building.getTotalFloors())
                .floorCount((int) floorCount)
                .zoneCount((int) zoneCount)
                .slotCount((int) slotCount)
                .createdSlots((int) slotCount)
                .maxCapacity(maxCapacity)
                .currentOccupancy(currentOccupancy)
                .status(building.getStatus())
                .operatingStartTime(building.getOperatingStartTime())
                .operatingEndTime(building.getOperatingEndTime())
                .createdAt(building.getCreatedAt())
                .updatedAt(building.getUpdatedAt())
                .build();
    }

    private ManagerSetupResponse toBuildingDetail(Building building) {
        return toBuildingSummary(building);
    }

    private ManagerSetupResponse toFloorResponse(Floor floor) {
        long zoneCount = zoneRepository.countByFloorFloorId(floor.getFloorId());
        return toFloorResponse(floor, zoneCount);
    }

    private ManagerSetupResponse toFloorResponse(Floor floor, long zoneCount) {
        VehicleType vehicleType = floor.getVehicleType();
        return ManagerSetupResponse.builder()
                .id(floor.getFloorId())
                .parentId(floor.getBuilding().getBuildingId())
                .name(floor.getFloorName())
                .type("FLOOR")
                .level(floor.getFloorLevel())
                .maxCapacity(floor.getMaxCapacity())
                .currentOccupancy(floor.getCurrentOccupancy())
                .zoneCount((int) zoneCount)
                .vehicleTypeId(vehicleType != null ? vehicleType.getVehicleTypeId() : null)
                .vehicleTypeName(vehicleType != null ? vehicleType.getTypeName() : null)
                .status(floor.getStatus())
                .createdAt(floor.getCreatedAt())
                .updatedAt(floor.getUpdatedAt())
                .build();
    }

    private ManagerSetupResponse toZoneResponse(Zone zone) {
        long slotCount = parkingSlotRepository.countByZoneZoneId(zone.getZoneId());
        return toZoneResponse(zone, slotCount);
    }

    private ManagerSetupResponse toZoneResponse(Zone zone, long slotCount) {
        return ManagerSetupResponse.builder()
                .id(zone.getZoneId())
                .parentId(zone.getFloor().getFloorId())
                .name(zone.getZoneName())
                .type("ZONE")
                .maxCapacity(zone.getMaxCapacity())
                .currentOccupancy(zone.getCurrentOccupancy())
                .slotCount((int) slotCount)
                .status(zone.getStatus())
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .build();
    }

    private Map<String, Long> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> row[1] instanceof Number n ? n.longValue() : 0L,
                (a, b) -> a));
    }

    private ManagerSetupResponse toSlotResponse(ParkingSlot slot) {
        return ManagerSetupResponse.builder()
                .id(slot.getSlotId())
                .parentId(slot.getZone().getZoneId())
                .name(slot.getSlotName())
                .type("SLOT")
                .status(slot.getSlotStatus())
                .note(slot.getNote())
                .createdAt(slot.getCreatedAt())
                .updatedAt(slot.getUpdatedAt())
                .build();
    }

    private SlotOccupancyDetailResponse toSlotOccupancyDetail(
            ParkingSlot slot,
            Reservation reservation,
            ParkingSession session,
            Ticket ticket) {
        Zone zone = slot.getZone();
        Floor floor = zone != null ? zone.getFloor() : null;
        Building building = floor != null ? floor.getBuilding() : null;

        SlotOccupancyDetailResponse.SlotOccupancyDetailResponseBuilder builder = SlotOccupancyDetailResponse.builder()
                .slotId(slot.getSlotId())
                .slotName(slot.getSlotName())
                .slotStatus(slot.getSlotStatus())
                .buildingId(building != null ? building.getBuildingId() : null)
                .buildingName(building != null ? building.getBuildingName() : null)
                .floorId(floor != null ? floor.getFloorId() : null)
                .floorName(floor != null ? floor.getFloorName() : null)
                .floorLevel(floor != null ? floor.getFloorLevel() : null)
                .zoneId(zone != null ? zone.getZoneId() : null)
                .zoneName(zone != null ? zone.getZoneName() : null);

        if (reservation != null) {
            builder.reservationId(reservation.getReservationId())
                    .reservationCode(reservation.getReservationCode())
                    .reservationStatus(reservation.getReservationStatus())
                    .reservationStart(reservation.getReservationStart());

            User driver = reservation.getUser();
            if (driver != null) {
                builder.driverId(driver.getUserId())
                        .driverUsername(driver.getUsername())
                        .driverFullName(driver.getFullName())
                        .driverEmail(driver.getEmail())
                        .driverPhoneNumber(driver.getPhoneNumber());
            }

            Vehicle vehicle = reservation.getVehicle();
            if (vehicle != null) {
                builder.vehicleId(vehicle.getVehicleId())
                        .vehiclePlateNumber(vehicle.getPlateNumber())
                        .vehicleBrand(vehicle.getBrand())
                        .vehicleModel(vehicle.getModel())
                        .vehicleColor(vehicle.getVehicleColor());
                VehicleType vehicleType = vehicle.getVehicleType();
                if (vehicleType != null) {
                    builder.vehicleTypeName(vehicleType.getTypeName());
                }
            }
        } else if (session != null) {
            // Guest session (no reservation) — vehicle linked directly on the session
            builder.guestName(session.getGuestName())
                    .guestPhone(session.getGuestPhone());

            Vehicle vehicle = session.getVehicle();
            if (vehicle != null) {
                builder.vehicleId(vehicle.getVehicleId())
                        .vehiclePlateNumber(vehicle.getPlateNumber())
                        .vehicleBrand(vehicle.getBrand())
                        .vehicleModel(vehicle.getModel())
                        .vehicleColor(vehicle.getVehicleColor());
                VehicleType vehicleType = vehicle.getVehicleType();
                if (vehicleType != null) {
                    builder.vehicleTypeName(vehicleType.getTypeName());
                }
            }
        }

        if (ticket != null) {
            builder.ticketCode(ticket.getTicketCode());
        }

        if (session != null) {
            builder.sessionId(session.getSessionId())
                    .sessionStatus(session.getSessionStatus())
                    .checkinTime(session.getCheckinTime())
                    .checkoutTime(session.getCheckoutTime())
                    .parkedDurationMinutes(calculateParkedDurationMinutes(session))
                    .checkinVehicleImage(session.getCheckinVehicleImage());
        }

        return builder.build();
    }

    private Long calculateParkedDurationMinutes(ParkingSession session) {
        LocalDateTime checkinTime = session.getCheckinTime();
        if (checkinTime == null) {
            return null;
        }
        LocalDateTime endTime = session.getCheckoutTime() != null
                ? session.getCheckoutTime()
                : LocalDateTime.now();
        return Duration.between(checkinTime, endTime).toMinutes();
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private int extractSlotIndex(ParkingSlot slot) {
        String slotName = slot.getSlotName();
        int lastDash = slotName.lastIndexOf('-');
        if (lastDash >= 0 && lastDash < slotName.length() - 1) {
            try {
                return Integer.parseInt(slotName.substring(lastDash + 1).trim());
            } catch (NumberFormatException ignored) {
                // Fall back to lexical ordering below.
            }
        }
        return -1;
    }

    private Comparator<ParkingSlot> slotByIndexAscending() {
        return (left, right) -> {
            int leftIndex = extractSlotIndex(left);
            int rightIndex = extractSlotIndex(right);
            if (leftIndex >= 0 && rightIndex >= 0 && leftIndex != rightIndex) {
                return Integer.compare(leftIndex, rightIndex);
            }
            return left.getSlotName().compareToIgnoreCase(right.getSlotName());
        };
    }

    private Comparator<ParkingSlot> slotByIndexDescending() {
        return slotByIndexAscending().reversed();
    }
}
