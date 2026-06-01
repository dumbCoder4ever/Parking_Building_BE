package fpt.swp391.parkingmanagement.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.CreateBuildingRequest;
import fpt.swp391.parkingmanagement.dto.CreateFloorRequest;
import fpt.swp391.parkingmanagement.dto.CreateZoneRequest;
import fpt.swp391.parkingmanagement.dto.ManagerSetupResponse;
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

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    @Transactional
    public ManagerSetupResponse createBuilding(CreateBuildingRequest request) {
        validateBuildingRequest(request);

        Building building = new Building();
        building.setBuildingName(normalizeText(request.getBuildingName()));
        building.setAddress(normalizeText(request.getAddress()));
        building.setTotalFloors(request.getTotalFloors());
        building.setOperatingStartTime(request.getOperatingStartTime());
        building.setOperatingEndTime(request.getOperatingEndTime());
        building.setContactNumber(normalizeText(request.getContactNumber()));
        building.setStatus("ACTIVE");

        Building saved = buildingRepository.save(building);
        return ManagerSetupResponse.builder()
                .id(saved.getBuildingId())
                .name(saved.getBuildingName())
                .type("BUILDING")
                .maxCapacity(0)
                .status(saved.getStatus())
                .operatingStartTime(saved.getOperatingStartTime())
                .operatingEndTime(saved.getOperatingEndTime())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public ManagerSetupResponse createFloor(String buildingId, CreateFloorRequest request) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found: " + buildingId));

        validateFloorRequest(building, request);

        Floor floor = new Floor();
        floor.setBuilding(building);
        floor.setFloorName(normalizeText(request.getFloorName()));
        floor.setFloorLevel(request.getFloorLevel());
        floor.setMaxCapacity(request.getMaxCapacity());
        floor.setCurrentOccupancy(0);
        floor.setStatus("ACTIVE");

        Floor saved = floorRepository.save(floor);
        return ManagerSetupResponse.builder()
                .id(saved.getFloorId())
                .parentId(building.getBuildingId())
                .name(saved.getFloorName())
                .type("FLOOR")
                .level(saved.getFloorLevel())
                .maxCapacity(saved.getMaxCapacity())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public ManagerSetupResponse createZoneAndSlots(String floorId, CreateZoneRequest request) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found: " + floorId));

        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle type not found: " + request.getVehicleTypeId()));

        validateZoneRequest(floor, request);

        Zone zone = new Zone();
        zone.setFloor(floor);
        zone.setVehicleType(vehicleType);
        zone.setZoneName(normalizeText(request.getZoneName()));
        zone.setMaxCapacity(request.getMaxCapacity());
        zone.setCurrentOccupancy(0);
        zone.setStatus("ACTIVE");
        Zone savedZone = zoneRepository.save(zone);

        List<ParkingSlot> slots = buildSlots(savedZone, normalizeText(request.getSlotPrefix()), request.getMaxCapacity());
        parkingSlotRepository.saveAll(slots);

        return ManagerSetupResponse.builder()
                .id(savedZone.getZoneId())
                .parentId(floor.getFloorId())
                .name(savedZone.getZoneName())
                .type("ZONE")
                .maxCapacity(savedZone.getMaxCapacity())
                .createdSlots(slots.size())
                .vehicleTypeId(vehicleType.getVehicleTypeId())
                .vehicleTypeName(vehicleType.getTypeName())
                .status(savedZone.getStatus())
                .createdAt(savedZone.getCreatedAt())
                .build();
    }

    private void validateBuildingRequest(CreateBuildingRequest request) {
        if (!request.getOperatingEndTime().isAfter(request.getOperatingStartTime())) {
            throw new RuntimeException("Operating end time must be after operating start time");
        }
    }

    private void validateFloorRequest(Building building, CreateFloorRequest request) {
        if (request.getFloorLevel() > building.getTotalFloors()) {
            throw new RuntimeException("Floor level exceeds building total floors");
        }
        if (floorRepository.existsByBuildingBuildingIdAndFloorLevel(building.getBuildingId(), request.getFloorLevel())) {
            throw new DuplicateResourceException("Floor level already exists in this building");
        }
        if (floorRepository.existsByBuildingBuildingIdAndFloorNameIgnoreCase(
                building.getBuildingId(), normalizeText(request.getFloorName()))) {
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

        int usedCapacity = zoneRepository.findByFloorFloorId(floor.getFloorId())
                .stream()
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
            slot.setNote(null);
            slots.add(slot);
        }
        return slots;
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }
}
