package fpt.swp391.parkingmanagement.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.CreateBuildingRequest;
import fpt.swp391.parkingmanagement.dto.CreateFloorRequest;
import fpt.swp391.parkingmanagement.dto.CreateZoneRequest;
import fpt.swp391.parkingmanagement.dto.ManagerSetupResponse;
import fpt.swp391.parkingmanagement.dto.UpdateBuildingRequest;
import fpt.swp391.parkingmanagement.dto.UpdateFloorRequest;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.exception.DuplicateResourceException;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import fpt.swp391.parkingmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ManagerBuildingSetupService {

    private static final Set<String> BUILDING_FLOOR_STATUSES = Set.of("ACTIVE", "INACTIVE", "MAINTENANCE");
    private static final Set<String> ZONE_STATUSES = Set.of("ACTIVE", "INACTIVE", "FULL", "MAINTENANCE");

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    @Transactional(readOnly = true)
    public List<ManagerSetupResponse> getAllBuildings() {
        return buildingRepository.findAll().stream()
                .map(this::toBuildingSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagerSetupResponse getBuilding(String buildingId) {
        Building building = findBuilding(buildingId);
        return toBuildingDetail(building);
    }

    @Transactional(readOnly = true)
    public List<ManagerSetupResponse> getFloorsByBuilding(String buildingId) {
        findBuilding(buildingId);
        return floorRepository.findByBuildingBuildingIdOrderByFloorLevelAsc(buildingId).stream()
                .map(this::toFloorResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ManagerSetupResponse> getZonesByFloor(String floorId) {
        Floor floor = findFloor(floorId);
        return zoneRepository.findByFloorFloorIdOrderByZoneNameAsc(floor.getFloorId()).stream()
                .map(this::toZoneResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ManagerSetupResponse> getSlotsByZone(String zoneId) {
        Zone zone = findZone(zoneId);
        return parkingSlotRepository.findByZoneZoneIdOrderBySlotNameAsc(zone.getZoneId()).stream()
                .map(this::toSlotResponse)
                .toList();
    }

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

    @Transactional
    public ManagerSetupResponse updateBuildingStatus(String buildingId, String status) {
        Building building = findBuilding(buildingId);
        building.setStatus(validateBuildingOrFloorStatus(status));
        return toBuildingDetail(buildingRepository.save(building));
    }

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

    @Transactional
    public ManagerSetupResponse updateFloor(String floorId, UpdateFloorRequest request) {
        Floor floor = findFloor(floorId);
        String normalizedName = normalizeText(request.getFloorName());

        if (floorRepository.existsByBuildingBuildingIdAndFloorNameIgnoreCaseAndFloorIdNot(
                floor.getBuilding().getBuildingId(), normalizedName, floorId)) {
            throw new DuplicateResourceException("Floor name already exists in this building");
        }

        int usedZoneCapacity = zoneRepository.findByFloorFloorId(floorId).stream()
                .map(Zone::getMaxCapacity)
                .filter(value -> value != null && value > 0)
                .reduce(0, Integer::sum);
        if (request.getMaxCapacity() < usedZoneCapacity) {
            throw new RuntimeException("Floor max capacity cannot be less than total zone capacity");
        }

        floor.setFloorName(normalizedName);
        floor.setMaxCapacity(request.getMaxCapacity());
        return toFloorResponse(floorRepository.save(floor));
    }

    @Transactional
    public ManagerSetupResponse updateFloorStatus(String floorId, String status) {
        Floor floor = findFloor(floorId);
        floor.setStatus(validateBuildingOrFloorStatus(status));
        return toFloorResponse(floorRepository.save(floor));
    }

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

        List<ParkingSlot> slots = buildSlots(savedZone, normalizeText(request.getSlotPrefix()), request.getMaxCapacity());
        parkingSlotRepository.saveAll(slots);

        ManagerSetupResponse response = toZoneResponse(savedZone);
        response.setCreatedSlots(slots.size());
        return response;
    }

    @Transactional
    public ManagerSetupResponse updateZoneStatus(String zoneId, String status) {
        Zone zone = findZone(zoneId);
        zone.setStatus(validateZoneStatus(status));
        return toZoneResponse(zoneRepository.save(zone));
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
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found: " + zoneId));
    }

    private VehicleType resolveVehicleType(String buildingId, CreateFloorRequest request) {
        String vehicleTypeId = normalizeText(request.getVehicleTypeId());

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
        List<ParkingSlot> slots = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
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

    private ManagerSetupResponse toBuildingSummary(Building building) {
        long floorCount = floorRepository.countByBuildingBuildingId(building.getBuildingId());
        return ManagerSetupResponse.builder()
                .id(building.getBuildingId())
                .name(building.getBuildingName())
                .type("BUILDING")
                .address(building.getAddress())
                .contactNumber(building.getContactNumber())
                .totalFloors(building.getTotalFloors())
                .floorCount((int) floorCount)
                .status(building.getStatus())
                .operatingStartTime(building.getOperatingStartTime())
                .operatingEndTime(building.getOperatingEndTime())
                .createdAt(building.getCreatedAt())
                .updatedAt(building.getUpdatedAt())
                .build();
    }

    private ManagerSetupResponse toBuildingDetail(Building building) {
        ManagerSetupResponse response = toBuildingSummary(building);
        response.setMaxCapacity(floorRepository.sumMaxCapacityByBuildingId(building.getBuildingId()));
        return response;
    }

    private ManagerSetupResponse toFloorResponse(Floor floor) {
        long zoneCount = zoneRepository.countByFloorFloorId(floor.getFloorId());
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

    private String normalizeText(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }
}
