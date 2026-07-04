package fpt.swp391.parkingmanagement.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.BuildingDetailDto;
import fpt.swp391.parkingmanagement.dto.BuildingFloorsDto;
import fpt.swp391.parkingmanagement.dto.BuildingInfoDto;
import fpt.swp391.parkingmanagement.dto.BuildingSummaryDto;
import fpt.swp391.parkingmanagement.dto.FloorWithZonesDto;
import fpt.swp391.parkingmanagement.dto.PricingPolicySummaryDto;
import fpt.swp391.parkingmanagement.dto.SlotDetailDto;
import fpt.swp391.parkingmanagement.dto.ZoneSlotsDto;
import fpt.swp391.parkingmanagement.dto.ZoneSummaryDto;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.ZoneRepository;
import fpt.swp391.parkingmanagement.repository.ZoneSlotCount;
import fpt.swp391.parkingmanagement.service.PricingService;

@Service
@Transactional(readOnly = true)
public class BuildingService {

    private static final List<String> ACTIVE_STATUSES = List.of("ACTIVE");

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final ReservationRepository reservationRepository;
    private final PricingService pricingService;

    public BuildingService(BuildingRepository buildingRepository,
                          FloorRepository floorRepository,
                          ZoneRepository zoneRepository,
                          ParkingSlotRepository parkingSlotRepository,
                          PricingPolicyRepository pricingPolicyRepository,
                          ReservationRepository reservationRepository,
                          PricingService pricingService) {
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.zoneRepository = zoneRepository;
        this.parkingSlotRepository = parkingSlotRepository;
        this.pricingPolicyRepository = pricingPolicyRepository;
        this.reservationRepository = reservationRepository;
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
    // STEP 2a - GET /api/buildings/{buildingId}/info
    // Returns: building info + pricing + slot counts (no floors)
    // Queries: 1 (building) + 1 (aggregate) + M (pricing per vt)
    // =============================================================================
    public BuildingInfoDto getBuildingInfo(String buildingId, String vehicleTypeId) {
        Building building = buildingRepository.findByBuildingId(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found: " + buildingId));

        List<ZoneSlotCount> allCounts = parkingSlotRepository.aggregateSlotCounts(buildingId, vehicleTypeId);

        List<String> vtIds = allCounts.stream()
                .map(ZoneSlotCount::getVehicleTypeId)
                .filter(vt -> vt != null)
                .distinct()
                .toList();

        Map<String, PricingPolicySummaryDto> policyByVt = new LinkedHashMap<>();
        for (String vtId : vtIds) {
            pricingPolicyRepository.findAllActiveForVehicleType(vtId).stream()
                    .filter(p -> "ACTIVE".equals(p.getStatus()))
                    .findFirst()
                    .ifPresent(p -> {
                        String vtName = allCounts.stream()
                                .filter(c -> vtId.equals(c.getVehicleTypeId()))
                                .findFirst()
                                .map(ZoneSlotCount::getVehicleTypeName)
                                .orElse(p.getVehicleType().getTypeName());
                        policyByVt.put(vtId, PricingPolicySummaryDto.builder()
                                .policyId(p.getPolicyId())
                                .vehicleTypeId(vtId)
                                .vehicleTypeName(vtName)
                                .pricingType(p.getPricingType())
                                .basePrice(p.getBasePrice())
                                .hourlyRate(p.getHourlyRate())
                                .maxHours(p.getMaxHours())
                                .build());
                    });
        }

        List<String> vehicleTypeNames = vtIds.stream()
                .map(vtId -> allCounts.stream()
                        .filter(c -> vtId.equals(c.getVehicleTypeId()))
                        .findFirst()
                        .map(ZoneSlotCount::getVehicleTypeName)
                        .orElse(null))
                .filter(vt -> vt != null)
                .distinct()
                .toList();

        long totalSlots = allCounts.stream().mapToLong(ZoneSlotCount::getTotalSlots).sum();
        long availableSlots = allCounts.stream().mapToLong(ZoneSlotCount::getAvailableSlots).sum();

        String operatingDisplay = null;
        if (building.getOperatingStartTime() != null && building.getOperatingEndTime() != null) {
            operatingDisplay = building.getOperatingStartTime() + " - " + building.getOperatingEndTime();
        }

        return BuildingInfoDto.builder()
                .buildingId(building.getBuildingId())
                .name(building.getBuildingName())
                .address(building.getAddress())
                .contactNumber(building.getContactNumber())
                .operatingStartTime(building.getOperatingStartTime())
                .operatingEndTime(building.getOperatingEndTime())
                .operatingHoursDisplay(operatingDisplay)
                .parkingRules("Vui lòng đặt trước chỗ đỗ xe. Xuất trình mã vé khi check-in. Giữ vé cẩn thận khi rời khỏi bãi đỗ.")
                .totalSlots(totalSlots)
                .availableSlots(availableSlots)
                .vehicleTypes(vehicleTypeNames)
                .pricingByType(policyByVt.values().stream().toList())
                .build();
    }

    // =============================================================================
    // STEP 2b - GET /api/buildings/{buildingId}/floors
    // Returns: floors + zones with slot counts (no building info)
    // Queries: 1 (floors) + 1 (aggregate)
    // =============================================================================
    public BuildingFloorsDto getBuildingFloors(String buildingId, String vehicleTypeId) {
        List<Floor> floors = floorRepository
                .findByBuildingBuildingIdOrderByFloorLevelAsc(buildingId).stream()
                .filter(f -> ACTIVE_STATUSES.contains(normalize(f.getStatus())))
                .toList();

        List<ZoneSlotCount> allCounts = parkingSlotRepository.aggregateSlotCounts(buildingId, vehicleTypeId);

        Map<String, List<ZoneSlotCount>> countsByFloor = allCounts.stream()
                .collect(Collectors.groupingBy(ZoneSlotCount::getFloorId));

        List<FloorWithZonesDto> floorDtos = new ArrayList<>(floors.size());
        for (Floor f : floors) {
            List<ZoneSlotCount> floorCounts = countsByFloor.getOrDefault(f.getFloorId(), List.of());
            Map<String, List<ZoneSlotCount>> countsByZone = floorCounts.stream()
                    .collect(Collectors.groupingBy(ZoneSlotCount::getZoneId));

            List<ZoneSummaryDto> zoneDtos = countsByZone.entrySet().stream()
                    .map(entry -> {
                        List<ZoneSlotCount> zoneCounts = entry.getValue();
                        ZoneSlotCount first = zoneCounts.get(0);
                        long zoneTotal = zoneCounts.stream().mapToLong(ZoneSlotCount::getTotalSlots).sum();
                        long zoneAvailable = zoneCounts.stream().mapToLong(ZoneSlotCount::getAvailableSlots).sum();
                        return ZoneSummaryDto.builder()
                                .zoneId(first.getZoneId())
                                .zoneName(first.getZoneName())
                                .zoneStatus(first.getZoneStatus())
                                .totalSlots(zoneTotal)
                                .availableSlots(zoneAvailable)
                                .build();
                    })
                    .toList();

            floorDtos.add(FloorWithZonesDto.builder()
                    .floorId(f.getFloorId())
                    .floorName(f.getFloorName())
                    .floorLevel(f.getFloorLevel())
                    .floorStatus(f.getStatus())
                    .vehicleTypeId(f.getVehicleType() != null ? f.getVehicleType().getVehicleTypeId() : null)
                    .vehicleTypeName(f.getVehicleType() != null ? f.getVehicleType().getTypeName() : null)
                    .zones(zoneDtos)
                    .build());
        }

        return BuildingFloorsDto.builder()
                .buildingId(buildingId)
                .floors(floorDtos)
                .build();
    }

    /**
     * @deprecated Use getBuildingInfo + getBuildingFloors for progressive loading.
     */
    @Deprecated
    public BuildingDetailDto getBuildingDetail(String buildingId, String vehicleTypeId) {
        BuildingInfoDto info = getBuildingInfo(buildingId, vehicleTypeId);
        BuildingFloorsDto floors = getBuildingFloors(buildingId, vehicleTypeId);

        return BuildingDetailDto.builder()
                .buildingId(info.getBuildingId())
                .name(info.getName())
                .address(info.getAddress())
                .contactNumber(info.getContactNumber())
                .operatingStartTime(info.getOperatingStartTime())
                .operatingEndTime(info.getOperatingEndTime())
                .operatingHoursDisplay(info.getOperatingHoursDisplay())
                .parkingRules(info.getParkingRules())
                .totalSlots(info.getTotalSlots())
                .availableSlots(info.getAvailableSlots())
                .vehicleTypes(info.getVehicleTypes())
                .pricingByType(info.getPricingByType())
                .floors(floors.getFloors())
                .build();
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

    private String normalize(String status) {
        return status != null ? status.trim().toUpperCase() : "";
    }
}
