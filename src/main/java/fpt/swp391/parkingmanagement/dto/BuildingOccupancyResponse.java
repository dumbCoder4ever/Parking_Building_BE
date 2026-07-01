package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingOccupancyResponse {
    private String buildingId;
    private String buildingName;
    private long totalSlots;
    private long availableSlots;
    private long occupiedSlots;
    private long reservedSlots;
    private long pendingExitSlots;
    /** Phần trăm lấp đầy = (occupied + reserved) / total * 100 */
    private double occupancyRate;
}
