package fpt.swp391.parkingmanagement.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code GET /api/buildings/{id}/floors}.
 * Returns the building header + its floors, each with zones and aggregate slot counts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingFloorsResponse {

    private String buildingId;
    private String buildingName;
    private String address;
    private String buildingStatus;

    private long totalSlots;
    private long availableSlots;

    private List<FloorWithZones> floors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FloorWithZones {
        private String floorId;
        private String floorName;
        private Integer floorLevel;
        private String floorStatus;
        private String vehicleTypeId;
        private String vehicleTypeName;

        private long totalSlots;
        private long availableSlots;

        private List<ZoneSlotSummary> zones;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZoneSlotSummary {
        private String zoneId;
        private String zoneName;
        private String zoneStatus;
        private long totalSlots;
        private long availableSlots;
        private long reservedSlots;
        private long occupiedSlots;
    }
}
