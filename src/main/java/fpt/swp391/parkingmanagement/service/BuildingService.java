package fpt.swp391.parkingmanagement.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.BuildingSummaryDto;
import fpt.swp391.parkingmanagement.dto.FloorDto;
import fpt.swp391.parkingmanagement.dto.PricingPolicySummaryDto;
import fpt.swp391.parkingmanagement.dto.SlotDetailDto;
import fpt.swp391.parkingmanagement.dto.SlotSummaryDto;
import fpt.swp391.parkingmanagement.dto.ZoneSlotsDto;
import fpt.swp391.parkingmanagement.dto.ZoneSummaryDto;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.BuildingVtSlotCount;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.FloorZoneAvailabilityRow;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.ZoneRepository;

@Service
@Transactional(readOnly = true)
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final ZoneRepository zoneRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final ReservationRepository reservationRepository;
    private final PricingService pricingService;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final FloorRepository floorRepository;
    private final BuildingRuleService buildingRuleService;

    public BuildingService(BuildingRepository buildingRepository,
                          ZoneRepository zoneRepository,
                          ParkingSlotRepository parkingSlotRepository,
                          ReservationRepository reservationRepository,
                          PricingService pricingService,
                          PricingPolicyRepository pricingPolicyRepository,
                          FloorRepository floorRepository,
                          BuildingRuleService buildingRuleService) {
        this.buildingRepository = buildingRepository;
        this.zoneRepository = zoneRepository;
        this.parkingSlotRepository = parkingSlotRepository;
        this.reservationRepository = reservationRepository;
        this.pricingService = pricingService;
        this.pricingPolicyRepository = pricingPolicyRepository;
        this.floorRepository = floorRepository;
        this.buildingRuleService = buildingRuleService;
    }

    // =============================================================================
    // STEP 1 - GET /api/buildings/available
    // Lightweight: 1 buildings + 1 building×vt aggregate + 1 batch pricing
    // =============================================================================
    @Cacheable(value = "buildingsAvailable",
            key = "(#vehicleTypeId ?: '') + '|' + (#name ?: '') + '|' + (#address ?: '')")
    public List<BuildingSummaryDto> listAvailableBuildings(String vehicleTypeId,
                                                          String name,
                                                          String address) {
        List<Building> buildings = buildingRepository.findActiveBuildings(name, address);
        if (buildings.isEmpty()) {
            return List.of();
        }

        List<BuildingVtSlotCount> allCounts = parkingSlotRepository
                .aggregateBuildingVtSlotCounts(hasText(vehicleTypeId) ? vehicleTypeId : null);

        Map<String, List<BuildingVtSlotCount>> countsByBuilding = allCounts.stream()
                .collect(Collectors.groupingBy(BuildingVtSlotCount::getBuildingId, LinkedHashMap::new, Collectors.toList()));

        Set<String> allVtIds = allCounts.stream()
                .map(BuildingVtSlotCount::getVehicleTypeId)
                .filter(vt -> vt != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, PricingPolicySummaryDto> pricingMap = loadPricingSummaries(allVtIds, allCounts);

        List<BuildingSummaryDto> result = new ArrayList<>(buildings.size());
        for (Building b : buildings) {
            List<BuildingVtSlotCount> buildingCounts =
                    countsByBuilding.getOrDefault(b.getBuildingId(), List.of());

            long total = buildingCounts.stream().mapToLong(BuildingVtSlotCount::getTotalSlots).sum();
            long available = buildingCounts.stream().mapToLong(BuildingVtSlotCount::getAvailableSlots).sum();

            List<String> vehicleTypeNames = buildingCounts.stream()
                    .map(BuildingVtSlotCount::getVehicleTypeName)
                    .filter(vt -> vt != null)
                    .distinct()
                    .toList();

            List<PricingPolicySummaryDto> pricingList = buildingCounts.stream()
                    .map(BuildingVtSlotCount::getVehicleTypeId)
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
                    .parkingRules(buildingRuleService.resolveParkingRulesText(b.getBuildingId()))
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
    // =============================================================================
    public ZoneSlotsDto getZoneSlots(String zoneId) {
        Zone zone = zoneRepository.findByZoneId(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found: " + zoneId));

        Floor floor = zone.getFloor();
        Building building = floor.getBuilding();

        List<ParkingSlot> slots = parkingSlotRepository.findByZoneZoneIdOrderBySlotNameAsc(zoneId);

        List<String> slotIds = slots.stream().map(ParkingSlot::getSlotId).toList();
        List<Reservation> reservations = slotIds.isEmpty()
                ? List.of()
                : reservationRepository.findBySlotSlotIdInAndReservationStatusInOrderByCreatedAtDesc(
                        slotIds, List.of("RESERVED", "APPROVED", "PENDING"));
        Map<String, Reservation> reservationBySlotId = reservations.stream()
                .collect(Collectors.toMap(r -> r.getSlot().getSlotId(), r -> r, (a, b) -> a));

        String vtId = floor.getVehicleType() != null ? floor.getVehicleType().getVehicleTypeId() : null;
        PricingPolicy policy = vtId != null ? pricingService.getActivePolicy(vtId) : null;

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

    @Transactional(readOnly = true)
    public List<FloorDto> listFloorsOfBuilding(String buildingId) {
        return listFloorsOfBuilding(buildingId, null);
    }

    /**
     * Floors + zone slot summaries in one DB round-trip (critical on remote Railway MySQL).
     */
    @Cacheable(value = "buildingFloors",
            key = "#buildingId + '|' + (#vehicleTypeId ?: '')")
    @Transactional(readOnly = true)
    public List<FloorDto> listFloorsOfBuilding(String buildingId, String vehicleTypeId) {
        List<FloorZoneAvailabilityRow> rows = floorRepository.findFloorZoneAvailability(
                buildingId, hasText(vehicleTypeId) ? vehicleTypeId : null);

        if (rows.isEmpty()) {
            if (!buildingRepository.existsById(buildingId)) {
                throw new ResourceNotFoundException("Building not found: " + buildingId);
            }
            return List.of();
        }

        Map<String, FloorDto.FloorDtoBuilder> floorBuilders = new LinkedHashMap<>();
        Map<String, List<ZoneSummaryDto>> zonesByFloor = new LinkedHashMap<>();

        for (FloorZoneAvailabilityRow row : rows) {
            floorBuilders.computeIfAbsent(row.getFloorId(), id -> FloorDto.builder()
                    .floorId(row.getFloorId())
                    .floorNumber(row.getFloorLevel())
                    .buildingId(row.getBuildingId() != null ? row.getBuildingId() : buildingId)
                    .floorStatus(row.getFloorStatus())
                    .vehicleTypeId(row.getVehicleTypeId())
                    .vehicleTypeName(row.getVehicleTypeName()));

            if (row.getZoneId() == null) {
                zonesByFloor.putIfAbsent(row.getFloorId(), new ArrayList<>());
                continue;
            }

            zonesByFloor
                    .computeIfAbsent(row.getFloorId(), id -> new ArrayList<>())
                    .add(ZoneSummaryDto.builder()
                            .zoneId(row.getZoneId())
                            .zoneName(row.getZoneName())
                            .zoneStatus(row.getZoneStatus())
                            .slotSummary(SlotSummaryDto.builder()
                                    .total(row.getTotalSlots())
                                    .available(row.getAvailableSlots())
                                    .build())
                            .build());
        }

        List<FloorDto> result = new ArrayList<>(floorBuilders.size());
        for (Map.Entry<String, FloorDto.FloorDtoBuilder> e : floorBuilders.entrySet()) {
            result.add(e.getValue()
                    .zones(zonesByFloor.getOrDefault(e.getKey(), List.of()))
                    .build());
        }
        return result;
    }

    private Map<String, PricingPolicySummaryDto> loadPricingSummaries(
            Set<String> vehicleTypeIds,
            List<BuildingVtSlotCount> counts) {
        Map<String, PricingPolicySummaryDto> pricingMap = new LinkedHashMap<>();
        if (vehicleTypeIds.isEmpty()) {
            return pricingMap;
        }

        Map<String, String> vtNameById = counts.stream()
                .filter(c -> c.getVehicleTypeId() != null)
                .collect(Collectors.toMap(
                        BuildingVtSlotCount::getVehicleTypeId,
                        BuildingVtSlotCount::getVehicleTypeName,
                        (a, b) -> a,
                        LinkedHashMap::new));

        for (PricingPolicy p : pricingPolicyRepository.findAllActiveForVehicleTypes(vehicleTypeIds)) {
            if (p.getVehicleType() == null || p.getVehicleType().getVehicleTypeId() == null) {
                continue;
            }
            String vtId = p.getVehicleType().getVehicleTypeId();
            pricingMap.putIfAbsent(vtId, PricingPolicySummaryDto.builder()
                    .policyId(p.getPolicyId())
                    .vehicleTypeId(vtId)
                    .vehicleTypeName(vtNameById.getOrDefault(vtId, p.getVehicleType().getTypeName()))
                    .pricingType(p.getPricingType())
                    .basePrice(p.getBasePrice())
                    .hourlyRate(p.getHourlyRate())
                    .maxHours(p.getMaxHours())
                    .build());
        }
        return pricingMap;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
