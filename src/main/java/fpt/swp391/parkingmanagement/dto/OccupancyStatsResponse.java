package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupancyStatsResponse {
    private long totalSlots;
    private long availableSlots;
    private long occupiedSlots;
    private long reservedSlots;
    private long pendingExitSlots;
    /** Phần trăm lấp đầy tổng hệ thống = (occupied + reserved) / total * 100 */
    private double occupancyRate;
    private List<BuildingOccupancyResponse> buildings;
}
