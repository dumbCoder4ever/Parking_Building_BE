package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.BuildingRevenueResponse;
import fpt.swp391.parkingmanagement.dto.RevenueDashboardResponse;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.BuildingRevenueProjection;
import fpt.swp391.parkingmanagement.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RevenueDashboardService {

    private final PaymentRepository paymentRepository;
    private final BuildingRepository buildingRepository;

    @Transactional(readOnly = true)
    public RevenueDashboardResponse getRevenueDashboard(LocalDateTime from, LocalDateTime to) {
        BigDecimal totalRevenue = paymentRepository.sumPaidRevenue(from, to);
        long totalPaymentCount = paymentRepository.countPaidPayments(from, to);

        Map<String, BuildingRevenueResponse> revenueByBuilding = new HashMap<>();
        for (BuildingRevenueProjection row : paymentRepository.sumRevenueByBuilding(from, to)) {
            revenueByBuilding.put(row.getBuildingId(), BuildingRevenueResponse.builder()
                    .buildingId(row.getBuildingId())
                    .buildingName(row.getBuildingName())
                    .totalRevenue(row.getTotalRevenue() != null ? row.getTotalRevenue() : BigDecimal.ZERO)
                    .paymentCount(row.getPaymentCount() != null ? row.getPaymentCount() : 0L)
                    .build());
        }

        List<BuildingRevenueResponse> buildings = new ArrayList<>();
        for (Building building : buildingRepository.findAll()) {
            BuildingRevenueResponse existing = revenueByBuilding.get(building.getBuildingId());
            if (existing != null) {
                buildings.add(existing);
            } else {
                buildings.add(BuildingRevenueResponse.builder()
                        .buildingId(building.getBuildingId())
                        .buildingName(building.getBuildingName())
                        .totalRevenue(BigDecimal.ZERO)
                        .paymentCount(0L)
                        .build());
            }
        }

        buildings.sort(Comparator
                .comparing(BuildingRevenueResponse::getTotalRevenue, Comparator.reverseOrder())
                .thenComparing(BuildingRevenueResponse::getBuildingName, String.CASE_INSENSITIVE_ORDER));

        return RevenueDashboardResponse.builder()
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .totalPaymentCount(totalPaymentCount)
                .from(from)
                .to(to)
                .buildings(buildings)
                .build();
    }
}
