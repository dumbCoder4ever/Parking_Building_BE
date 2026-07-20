package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.PeakHourAnalysisResponse;
import fpt.swp391.parkingmanagement.dto.PeakHourBucketResponse;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PeakHourService {

    private final ParkingSessionRepository parkingSessionRepository;
    private final SystemConfigService systemConfigService;

    @Transactional(readOnly = true)
    public PeakHourAnalysisResponse analyze(String buildingId, LocalDate fromDay, LocalDate toDay) {
        LocalDate resolvedFrom = fromDay != null ? fromDay : LocalDate.now().minusDays(6);
        LocalDate resolvedTo = toDay != null ? toDay : LocalDate.now();
        LocalDateTime from = resolvedFrom.atStartOfDay();
        LocalDateTime to = resolvedTo.atTime(23, 59, 59);

        long[] counts = new long[24];
        List<Object[]> rows = parkingSessionRepository.countCheckinsByHour(buildingId, from, to);
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            int hour = ((Number) row[0]).intValue();
            long count = row[1] instanceof Number n ? n.longValue() : 0L;
            if (hour >= 0 && hour < 24) {
                counts[hour] = count;
            }
        }

        double sum = 0;
        for (long c : counts) {
            sum += c;
        }
        double average = sum / 24.0;
        double variance = 0;
        for (long c : counts) {
            variance += (c - average) * (c - average);
        }
        double stdDev = Math.sqrt(variance / 24.0);
        double factor = systemConfigService.getDouble(SystemConfigService.PEAK_HOUR_STDDEV_FACTOR, 1.0);
        double threshold = average + stdDev * factor;

        // Also mark top-3 hours as peak so sparse data still surfaces peaks
        List<Integer> ranked = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            ranked.add(h);
        }
        ranked.sort((a, b) -> Long.compare(counts[b], counts[a]));
        Set<Integer> top3 = new HashSet<>(ranked.subList(0, Math.min(3, ranked.size())));

        List<Integer> peakHours = new ArrayList<>();
        List<PeakHourBucketResponse> buckets = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            boolean peak = counts[h] >= threshold && counts[h] > 0 || (top3.contains(h) && counts[h] > 0);
            if (peak) {
                peakHours.add(h);
            }
            buckets.add(PeakHourBucketResponse.builder()
                    .hour(h)
                    .sessionCount(counts[h])
                    .peak(peak)
                    .build());
        }

        return PeakHourAnalysisResponse.builder()
                .buildingId(buildingId)
                .fromDay(resolvedFrom.toString())
                .toDay(resolvedTo.toString())
                .averagePerHour(Math.round(average * 10.0) / 10.0)
                .peakThreshold(Math.round(threshold * 10.0) / 10.0)
                .peakHours(peakHours)
                .buckets(buckets)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isPeakHour(String buildingId, LocalDateTime at) {
        if (at == null) {
            return false;
        }
        PeakHourAnalysisResponse analysis = analyze(buildingId, at.toLocalDate().minusDays(6), at.toLocalDate());
        return analysis.getPeakHours() != null && analysis.getPeakHours().contains(at.getHour());
    }
}
