package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.SlotSuggestionResponse;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SlotSuggestionService {

    private final ParkingSlotRepository parkingSlotRepository;

    @Transactional(readOnly = true)
    public List<SlotSuggestionResponse> suggest(String buildingId, String vehicleTypeId, int limit) {
        if (buildingId == null || buildingId.isBlank() || vehicleTypeId == null || vehicleTypeId.isBlank()) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST, "buildingId and vehicleTypeId are required");
        }
        int topN = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 20));

        List<ParkingSlot> candidates = parkingSlotRepository.findAvailableByBuildingAndVehicleType(
                buildingId, vehicleTypeId, PageRequest.of(0, 50));
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, long[]> zoneStats = new HashMap<>();
        for (ParkingSlot slot : candidates) {
            Zone zone = slot.getZone();
            if (zone == null) {
                continue;
            }
            zoneStats.computeIfAbsent(zone.getZoneId(), id -> {
                long total = parkingSlotRepository.countByZoneZoneId(id);
                long occupied = parkingSlotRepository.countByZoneZoneIdAndSlotStatusIgnoreCase(id, "OCCUPIED")
                        + parkingSlotRepository.countByZoneZoneIdAndSlotStatusIgnoreCase(id, "RESERVED");
                return new long[]{total, occupied};
            });
        }

        List<SlotSuggestionResponse> scored = new ArrayList<>();
        for (ParkingSlot slot : candidates) {
            Zone zone = slot.getZone();
            Floor floor = zone != null ? zone.getFloor() : null;
            int floorLevel = floor != null && floor.getFloorLevel() != null ? floor.getFloorLevel() : 99;
            long[] stats = zone != null ? zoneStats.getOrDefault(zone.getZoneId(), new long[]{1, 0}) : new long[]{1, 0};
            double occupancyRatio = stats[0] > 0 ? (double) stats[1] / stats[0] : 0;

            // Lower floor + less occupied zone => higher score
            double score = 100.0 - (floorLevel * 8.0) - (occupancyRatio * 40.0);
            String reason = "Ưu tiên tầng thấp (L" + floorLevel + ")"
                    + ", zone occupancy " + Math.round(occupancyRatio * 100) + "%";

            scored.add(SlotSuggestionResponse.builder()
                    .slotId(slot.getSlotId())
                    .slotName(slot.getSlotName())
                    .zoneId(zone != null ? zone.getZoneId() : null)
                    .zoneName(zone != null ? zone.getZoneName() : null)
                    .floorId(floor != null ? floor.getFloorId() : null)
                    .floorName(floor != null ? floor.getFloorName() : null)
                    .floorLevel(floor != null ? floor.getFloorLevel() : null)
                    .score(Math.round(score * 10.0) / 10.0)
                    .reason(reason)
                    .build());
        }

        scored.sort(Comparator.comparingDouble(SlotSuggestionResponse::getScore).reversed());
        return scored.size() <= topN ? scored : scored.subList(0, topN);
    }
}
