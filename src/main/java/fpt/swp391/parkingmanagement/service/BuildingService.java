package fpt.swp391.parkingmanagement.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.BuildingFloorsResponse;
import fpt.swp391.parkingmanagement.dto.BuildingSummaryDto;
import fpt.swp391.parkingmanagement.dto.PricingPolicySummaryDto;
import fpt.swp391.parkingmanagement.dto.SlotDetailDto;
import fpt.swp391.parkingmanagement.dto.ZoneSlotsDto;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.ZoneRepository;
import fpt.swp391.parkingmanagement.repository.ZoneSlotCount;
import fpt.swp391.parkingmanagement.service.PricingService;

@Service
@Transactional(readOnly = true)
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final ZoneRepository zoneRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final ReservationRepository reservationRepository;
    private final FloorRepository floorRepository;
    private final PricingService pricingService;

    public BuildingService(BuildingRepository buildingRepository,
                          ZoneRepository zoneRepository,
                          ParkingSlotRepository parkingSlotRepository,
                          ReservationRepository reservationRepository,
                          FloorRepository floorRepository,
                          PricingService pricingService) {
        this.buildingRepository = buildingRepository;
        this.zoneRepository = zoneRepository;
        this.parkingSlotRepository = parkingSlotRepository;
        this.reservationRepository = reservationRepository;
        this.floorRepository = floorRepository;
        this.pricingService = pricingService;
    }

    // =============================================================================
    // STEP 1 - GET /api/buildings/available
    // Returns: list of active buildings with slot counts + supported vehicle types
    // Queries: 1 (buildings) + 1 (aggregate counts) + 1 (pricing per unique vt)
    // =============================================================================
    public List<BuildingSummaryDto> listAvailableBuildings(String vehicleTypeId,
                                                          String name,
                                                          String address) {
        // QUERY 1: active buildings filtered by name/address
        List<Building> buildings = buildingRepository.findActiveBuildings(name, address);

        if (buildings.isEmpty()) {
            return List.of();
        }

        // QUERY 2: aggregate slot counts per zone (already handles vehicleTypeId filter)
        List<ZoneSlotCount> allCounts = parkingSlotRepository
                .aggregateSlotCounts(null, vehicleTypeId);

        // Group counts by buildingId
        Map<String, List<ZoneSlotCount>> countsByBuilding = allCounts.stream()
                .collect(Collectors.groupingBy(ZoneSlotCount::getBuildingId));

        // Get unique vehicle type IDs across all buildings
        List<String> allVtIds = allCounts.stream()
                .map(ZoneSlotCount::getVehicleTypeId)
                .filter(vt -> vt != null)
                .distinct()
                .toList();

        // QUERY 3: pricing per unique vehicle type (N queries where N = unique vt count, bounded)
        // Vehicle types are already loaded in ZoneSlotCount via the aggregate query — no extra DB hit needed.
        Map<String, PricingPolicySummaryDto> pricingMap = new LinkedHashMap<>();
        for (String vtId : allVtIds) {
            PricingPolicy policy = pricingService.getActivePolicy(vtId);
            if (policy != null) {
                // vehicleType already available from ZoneSlotCount → no extra query needed
                PricingPolicySummaryDto dto = pricingMap.get(vtId);
                if (dto == null) {
                    // extract name from ZoneSlotCount instead of querying VehicleTypeRepository
                    String vtName = allCounts.stream()
                            .filter(c -> vtId.equals(c.getVehicleTypeId()))
                            .findFirst()
                            .map(ZoneSlotCount::getVehicleTypeName)
                            .orElse(null);
                    pricingMap.put(vtId, PricingPolicySummaryDto.builder()
                            .policyId(policy.getPolicyId())
                            .vehicleTypeId(vtId)
                            .vehicleTypeName(vtName)
                            .pricingType(policy.getPricingType())
                            .basePrice(policy.getBasePrice())
                            .hourlyRate(policy.getHourlyRate())
                            .maxHours(policy.getMaxHours())
                            .build());
                }
            }
        }

        // Build each building summary
        List<BuildingSummaryDto> result = new ArrayList<>(buildings.size());
        for (Building b : buildings) {
            List<ZoneSlotCount> buildingCounts = countsByBuilding.getOrDefault(b.getBuildingId(), List.of());

            long total = buildingCounts.stream().mapToLong(ZoneSlotCount::getTotalSlots).sum();
            long available = buildingCounts.stream().mapToLong(ZoneSlotCount::getAvailableSlots).sum();

            // Get unique vehicle type names for this building
            List<String> vehicleTypeNames = buildingCounts.stream()
                    .map(ZoneSlotCount::getVehicleTypeName)
                    .filter(vt -> vt != null)
                    .distinct()
                    .toList();

            // Pricing by vehicle type for this building
            List<PricingPolicySummaryDto> pricingList = buildingCounts.stream()
                    .map(ZoneSlotCount::getVehicleTypeId)
                    .filter(vt -> vt != null)
                    .distinct()
                    .map(pricingMap::get)
                    .filter(p -> p != null)
                    .toList();

            String operatingDisplay = null;
            if (b.getOperatingStartTime() != null && b.getOperatingEndTime() != null) {
                operatingDisplay = b.getOperatingStartTime() + " - " + b.getOperatingEndTime();
            }

            result.add(BuildingSummaryDto.builder()
                    .buildingId(b.getBuildingId())
                    .name(b.getBuildingName())
                    .address(b.getAddress())
                    .contactNumber(b.getContactNumber())
                    .operatingStartTime(b.getOperatingStartTime())
                    .operatingEndTime(b.getOperatingEndTime())
                    .operatingHoursDisplay(operatingDisplay)
                    .parkingRules("Vui lòng đặt trước chỗ đỗ xe. Xuất trình mã vé khi check-in. Giữ vé cẩn thận khi rời khỏi bãi đỗ.")
                    .totalSlots(total)
                    .availableSlots(available)
                    .vehicleTypes(vehicleTypeNames)
                    .pricingByType(pricingList)
                    .build());
        }
        return result;
    }

    // =============================================================================
    // STEP 3 - GET /api/zones/{zoneId}/slots
    // Returns: zone + slots with pricing + reservation info
    // Queries: 1 (zone+floor+building) + 1 (slots) + 1 (reservations) + 1 (pricing)
    // =============================================================================
    public ZoneSlotsDto getZoneSlots(String zoneId) {
        // QUERY 1: zone with floor + building + vehicle type (EntityGraph)
        Zone zone = zoneRepository.findByZoneId(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found: " + zoneId));

        Floor floor = zone.getFloor();
        Building building = floor.getBuilding();

        // QUERY 2: slots for this zone (EntityGraph already loads zone+floor+building+vehicleType)
        List<ParkingSlot> slots = parkingSlotRepository.findByZoneZoneIdOrderBySlotNameAsc(zoneId);

        // QUERY 3: active reservations for these slots
        List<String> slotIds = slots.stream().map(ParkingSlot::getSlotId).toList();
        List<Reservation> reservations = reservationRepository
                .findBySlotSlotIdInAndReservationStatusInOrderByCreatedAtDesc(
                        slotIds, List.of("RESERVED", "APPROVED", "PENDING"));
        Map<String, Reservation> reservationBySlotId = reservations.stream()
                .collect(Collectors.toMap(r -> r.getSlot().getSlotId(), r -> r, (a, b) -> a));

        // QUERY 4: pricing policy for vehicle type of this zone
        String vtId = floor.getVehicleType() != null ? floor.getVehicleType().getVehicleTypeId() : null;
        PricingPolicy policy = vtId != null ? pricingService.getActivePolicy(vtId) : null;

        // Build slot DTOs
        List<SlotDetailDto> slotDtos = slots.stream()
                .map(slot -> {
                    Reservation res = reservationBySlotId.get(slot.getSlotId());
                    String resUserId = null, resUsername = null, resVehicleId = null;
                    if (res != null) {
                        if (res.getUser() != null) {
                            resUserId = res.getUser().getUserId();
                            resUsername = res.getUser().getUsername();
                        }
                        if (res.getVehicle() != null) {
                            resVehicleId = res.getVehicle().getVehicleId();
                        }
                    }
                    return SlotDetailDto.builder()
                            .slotId(slot.getSlotId())
                            .slotName(slot.getSlotName())
                            .slotStatus(slot.getSlotStatus())
                            .vehicleTypeId(vtId)
                            .vehicleTypeName(floor.getVehicleType() != null ? floor.getVehicleType().getTypeName() : null)
                            .availableCount("AVAILABLE".equalsIgnoreCase(slot.getSlotStatus()) ? 1 : 0)
                            .basePrice(policy != null ? policy.getBasePrice() : null)
                            .hourlyRate(policy != null ? policy.getHourlyRate() : null)
                            .maxHours(policy != null ? policy.getMaxHours() : null)
                            .reservedByUserId(resUserId)
                            .reservedByUsername(resUsername)
                            .reservedByVehicleId(resVehicleId)
                            .build();
                })
                .toList();

        long total = slots.size();
        long available = slots.stream()
                .filter(s -> "AVAILABLE".equalsIgnoreCase(s.getSlotStatus()))
                .count();

        String operatingDisplay = null;
        if (building.getOperatingStartTime() != null && building.getOperatingEndTime() != null) {
            operatingDisplay = building.getOperatingStartTime() + " - " + building.getOperatingEndTime();
        }

        return ZoneSlotsDto.builder()
                .buildingId(building.getBuildingId())
                .buildingName(building.getBuildingName())
                .zoneId(zone.getZoneId())
                .zoneName(zone.getZoneName())
                .zoneStatus(zone.getStatus())
                .floorId(floor.getFloorId())
                .floorName(floor.getFloorName())
                .floorLevel(floor.getFloorLevel())
                .floorVehicleTypeId(vtId)
                .floorVehicleTypeName(floor.getVehicleType() != null ? floor.getVehicleType().getTypeName() : null)
                .totalSlots(total)
                .availableSlots(available)
                .slots(slotDtos)
                .build();
    }

    // =============================================================================
    // GET /api/buildings/{id}/floors
    // Returns: building header + ordered floors, each with zones and slot counts.
    // Queries: 1 (building) + 1 (floors with EntityGraph) + 1 (aggregate slot counts per zone)
    // =============================================================================
    public BuildingFloorsResponse listFloorsOfBuilding(String buildingId) {
        Building building = buildingRepository.findByBuildingId(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found: " + buildingId));

        List<Floor> floors = floorRepository.findByBuildingBuildingIdOrderByFloorLevelAsc(buildingId);
        if (floors.isEmpty()) {
            return BuildingFloorsResponse.builder()
                    .buildingId(building.getBuildingId())
                    .buildingName(building.getBuildingName())
                    .address(building.getAddress())
                    .buildingStatus(building.getStatus())
                    .totalSlots(0)
                    .availableSlots(0)
                    .floors(List.of())
                    .build();
        }

        List<ZoneSlotCount> zoneCounts = parkingSlotRepository.aggregateSlotCounts(buildingId, null);

        Map<String, List<ZoneSlotCount>> countsByFloor = zoneCounts.stream()
                .collect(Collectors.groupingBy(ZoneSlotCount::getFloorId));

        List<BuildingFloorsResponse.FloorWithZones> floorDtos = floors.stream()
                .map(floor -> {
                    VehicleType vt = floor.getVehicleType();
                    List<ZoneSlotCount> floorZoneCounts = countsByFloor.getOrDefault(floor.getFloorId(), List.of());

                    List<BuildingFloorsResponse.ZoneSlotSummary> zoneSummaries = floorZoneCounts.stream()
                            .map(zc -> BuildingFloorsResponse.ZoneSlotSummary.builder()
                                    .zoneId(zc.getZoneId())
                                    .zoneName(zc.getZoneName())
                                    .zoneStatus(zc.getZoneStatus())
                                    .totalSlots(zc.getTotalSlots() != null ? zc.getTotalSlots() : 0L)
                                    .availableSlots(zc.getAvailableSlots() != null ? zc.getAvailableSlots() : 0L)
                                    .reservedSlots(zc.getReservedSlots() != null ? zc.getReservedSlots() : 0L)
                                    .occupiedSlots(zc.getOccupiedSlots() != null ? zc.getOccupiedSlots() : 0L)
                                    .build())
                            .toList();

                    long floorTotal = floorZoneCounts.stream()
                            .mapToLong(zc -> zc.getTotalSlots() != null ? zc.getTotalSlots() : 0L)
                            .sum();
                    long floorAvailable = floorZoneCounts.stream()
                            .mapToLong(zc -> zc.getAvailableSlots() != null ? zc.getAvailableSlots() : 0L)
                            .sum();

                    return BuildingFloorsResponse.FloorWithZones.builder()
                            .floorId(floor.getFloorId())
                            .floorName(floor.getFloorName())
                            .floorLevel(floor.getFloorLevel())
                            .floorStatus(floor.getStatus())
                            .vehicleTypeId(vt != null ? vt.getVehicleTypeId() : null)
                            .vehicleTypeName(vt != null ? vt.getTypeName() : null)
                            .totalSlots(floorTotal)
                            .availableSlots(floorAvailable)
                            .zones(zoneSummaries)
                            .build();
                })
                .toList();

        long totalSlots = floorDtos.stream().mapToLong(BuildingFloorsResponse.FloorWithZones::getTotalSlots).sum();
        long availableSlots = floorDtos.stream().mapToLong(BuildingFloorsResponse.FloorWithZones::getAvailableSlots).sum();

        return BuildingFloorsResponse.builder()
                .buildingId(building.getBuildingId())
                .buildingName(building.getBuildingName())
                .address(building.getAddress())
                .buildingStatus(building.getStatus())
                .totalSlots(totalSlots)
                .availableSlots(availableSlots)
                .floors(floorDtos)
                .build();
    }
}
