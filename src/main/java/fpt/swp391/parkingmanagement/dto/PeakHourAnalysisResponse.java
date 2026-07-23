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
public class PeakHourAnalysisResponse {
    private String buildingId;
    private String fromDay;
    private String toDay;
    private double averagePerHour;
    private double peakThreshold;
    private List<Integer> peakHours;
    private List<PeakHourBucketResponse> buckets;
}
