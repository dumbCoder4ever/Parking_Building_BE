package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.*;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private final ParkingSlotRepository parkingSlotRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final IncidentRepository incidentRepository;
    private final BuildingRepository buildingRepository;

    @Cacheable(value = "dashboardStats", key = "#fromDay + '_' + #toDay")
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(LocalDate fromDay, LocalDate toDay) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfToday = startOfToday.plusDays(1);
        LocalDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay();

        LocalDate resolvedFrom = (fromDay != null) ? fromDay : now.toLocalDate().minusDays(6);
        LocalDate resolvedTo = (toDay != null) ? toDay : now.toLocalDate();
        LocalDateTime trendFrom = resolvedFrom.atStartOfDay();
        LocalDateTime trendTo = resolvedTo.atTime(23, 59, 59);

        return DashboardStatsResponse.builder()
                .generatedAt(now)
                .occupancy(buildOccupancyStats())
                .sessions(buildSessionStats(startOfToday, endOfToday, startOfMonth, now))
                .reservations(buildReservationStats(startOfToday, endOfToday))
                .users(buildUserStats(startOfMonth, now))
                .incidents(buildIncidentStats(startOfMonth, now))
                .revenueByPaymentMethod(buildPaymentMethodStats())
                .revenueTrend(buildRevenueTrend(trendFrom, trendTo))
                .build();
    }

    private OccupancyStatsResponse buildOccupancyStats() {
        List<ZoneSlotCount> zoneCounts = parkingSlotRepository.aggregateSlotCounts(null, null);

        long total = 0;
        long available = 0;
        long occupied = 0;
        long reserved = 0;
        long pendingExit = 0;

        Map<String, BuildingOccupancyResponse.BuildingOccupancyResponseBuilder> buildingBuilders = new LinkedHashMap<>();
        Map<String, long[]> buildingTallies = new LinkedHashMap<>(); // total, avail, occ, reserved, pending

        for (ZoneSlotCount z : zoneCounts) {
            long zTotal = nz(z.getTotalSlots());
            long zAvail = nz(z.getAvailableSlots());
            long zOcc = nz(z.getOccupiedSlots());
            long zRes = nz(z.getReservedSlots());
            long zPend = nz(z.getPendingExitSlots());

            total += zTotal;
            available += zAvail;
            occupied += zOcc;
            reserved += zRes;
            pendingExit += zPend;

            String bid = z.getBuildingId();
            buildingTallies.computeIfAbsent(bid, id -> {
                buildingBuilders.put(id, BuildingOccupancyResponse.builder()
                        .buildingId(id)
                        .buildingName(z.getBuildingName()));
                return new long[5];
            });
            long[] t = buildingTallies.get(bid);
            t[0] += zTotal;
            t[1] += zAvail;
            t[2] += zOcc;
            t[3] += zRes;
            t[4] += zPend;
        }

        // Buildings without slots still appear with zeros
        for (Building b : buildingRepository.findAll()) {
            buildingTallies.computeIfAbsent(b.getBuildingId(), id -> {
                buildingBuilders.put(id, BuildingOccupancyResponse.builder()
                        .buildingId(id)
                        .buildingName(b.getBuildingName()));
                return new long[5];
            });
        }

        List<BuildingOccupancyResponse> buildingList = new ArrayList<>();
        for (Map.Entry<String, long[]> e : buildingTallies.entrySet()) {
            long[] t = e.getValue();
            double bRate = t[0] > 0 ? Math.round((double) (t[2] + t[3]) / t[0] * 1000.0) / 10.0 : 0.0;
            buildingList.add(buildingBuilders.get(e.getKey())
                    .totalSlots(t[0])
                    .availableSlots(t[1])
                    .occupiedSlots(t[2])
                    .reservedSlots(t[3])
                    .pendingExitSlots(t[4])
                    .occupancyRate(bRate)
                    .build());
        }

        double rate = total > 0 ? Math.round((double) (occupied + reserved) / total * 1000.0) / 10.0 : 0.0;
        return OccupancyStatsResponse.builder()
                .totalSlots(total)
                .availableSlots(available)
                .occupiedSlots(occupied)
                .reservedSlots(reserved)
                .pendingExitSlots(pendingExit)
                .occupancyRate(rate)
                .buildings(buildingList)
                .build();
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }

    private SessionStatsResponse buildSessionStats(LocalDateTime startOfToday, LocalDateTime endOfToday,
                                                    LocalDateTime startOfMonth, LocalDateTime now) {
        long active = parkingSessionRepository.countBySessionStatus("ACTIVE");
        long today = parkingSessionRepository.countSessionsInRange(startOfToday, endOfToday);
        long thisMonth = parkingSessionRepository.countSessionsInRange(startOfMonth, now);
        long guestToday = parkingSessionRepository.countGuestSessionsInRange(startOfToday, endOfToday);
        Double avgDuration = parkingSessionRepository.avgDurationMinutesCompleted();
        Double avgFeeRaw = parkingSessionRepository.avgFeeCompleted();
        BigDecimal avgFee = avgFeeRaw != null
                ? BigDecimal.valueOf(avgFeeRaw).setScale(0, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return SessionStatsResponse.builder()
                .totalActiveSessions(active)
                .sessionsToday(today)
                .sessionsThisMonth(thisMonth)
                .guestSessionsToday(guestToday)
                .registeredSessionsToday(today - guestToday)
                .totalGuestSessionsAllTime(parkingSessionRepository.countGuestSessionsAllTime())
                .totalDriverSessionsAllTime(parkingSessionRepository.countDriverSessionsAllTime())
                .activeGuestSessions(parkingSessionRepository.countActiveGuestSessions())
                .activeDriverSessions(parkingSessionRepository.countActiveDriverSessions())
                .avgDurationMinutes(avgDuration != null ? Math.round(avgDuration * 10.0) / 10.0 : 0.0)
                .avgFee(avgFee)
                .build();
    }

    private ReservationStatsResponse buildReservationStats(LocalDateTime startOfToday, LocalDateTime endOfToday) {
        return ReservationStatsResponse.builder()
                .totalPending(reservationRepository.countByReservationStatus("PENDING"))
                .totalApproved(reservationRepository.countByReservationStatus("APPROVED"))
                .totalCompleted(reservationRepository.countByReservationStatus("COMPLETED"))
                .totalCancelled(reservationRepository.countByReservationStatus("CANCELLED"))
                .totalExpired(reservationRepository.countByReservationStatus("EXPIRED"))
                .totalToday(reservationRepository.countReservationsInRange(startOfToday, endOfToday))
                .build();
    }

    private UserStatsResponse buildUserStats(LocalDateTime startOfMonth, LocalDateTime now) {
        return UserStatsResponse.builder()
                .totalDrivers(userRepository.countActiveByRole("ROLE_DRIVER"))
                .totalStaff(userRepository.countActiveByRole("ROLE_STAFF"))
                .totalManagers(userRepository.countActiveByRole("ROLE_MANAGER"))
                .newUsersThisMonth(userRepository.countNewUsersInRange(startOfMonth, now))
                .driversCurrentlyParked(parkingSessionRepository.countDistinctDriversCurrentlyParked())
                .build();
    }

    private IncidentStatsResponse buildIncidentStats(LocalDateTime startOfMonth, LocalDateTime now) {
        return IncidentStatsResponse.builder()
                .totalOpen(incidentRepository.countByStatus("OPEN"))
                .totalThisMonth(incidentRepository.countInRange(startOfMonth, now))
                .totalAllTime(incidentRepository.count())
                .build();
    }

    private List<PaymentMethodStatsResponse> buildPaymentMethodStats() {
        return paymentRepository.sumRevenueByPaymentMethod(null, null).stream()
                .map(p -> PaymentMethodStatsResponse.builder()
                        .method(p.getPaymentMethod())
                        .totalRevenue(p.getTotalRevenue() != null ? p.getTotalRevenue() : BigDecimal.ZERO)
                        .count(p.getCount() != null ? p.getCount() : 0L)
                        .build())
                .collect(Collectors.toList());
    }

    private List<RevenueTrendItem> buildRevenueTrend(LocalDateTime from, LocalDateTime to) {
        Map<String, RevenueTrendItem> trendMap = new LinkedHashMap<>();
        for (RevenueTrendProjection p : paymentRepository.getRevenueTrend(from, to)) {
            trendMap.put(p.getDate(), RevenueTrendItem.builder()
                    .date(p.getDate())
                    .revenue(p.getRevenue() != null ? p.getRevenue() : BigDecimal.ZERO)
                    .count(p.getCount() != null ? p.getCount() : 0L)
                    .build());
        }

        // Fill all dates in range; days with no transactions get revenue = 0
        List<RevenueTrendItem> result = new ArrayList<>();
        LocalDate cursor = from.toLocalDate();
        LocalDate end = to.toLocalDate();
        while (!cursor.isAfter(end)) {
            String key = cursor.toString();
            result.add(trendMap.getOrDefault(key, RevenueTrendItem.builder()
                    .date(key)
                    .revenue(BigDecimal.ZERO)
                    .count(0L)
                    .build()));
            cursor = cursor.plusDays(1);
        }
        return result;
    }
}
