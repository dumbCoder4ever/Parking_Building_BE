package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.*;
import fpt.swp391.parkingmanagement.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class DashboardStatsService {

    private final ParkingSlotRepository parkingSlotRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final IncidentRepository incidentRepository;
    private final BuildingStaffRepository buildingStaffRepository;
    private final Executor dashboardExecutor;

    public DashboardStatsService(
            ParkingSlotRepository parkingSlotRepository,
            ParkingSessionRepository parkingSessionRepository,
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            IncidentRepository incidentRepository,
            BuildingStaffRepository buildingStaffRepository,
            @Qualifier("dashboardExecutor") Executor dashboardExecutor) {
        this.parkingSlotRepository = parkingSlotRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.incidentRepository = incidentRepository;
        this.buildingStaffRepository = buildingStaffRepository;
        this.dashboardExecutor = dashboardExecutor;
    }

    @Cacheable(value = "dashboardStats", key = "#fromDay + '_' + #toDay + '_' + (#buildingId ?: 'ALL')")
    public DashboardStatsResponse getStats(LocalDate fromDay, LocalDate toDay, String buildingId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfToday = startOfToday.plusDays(1);
        LocalDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay();

        LocalDate resolvedFrom = (fromDay != null) ? fromDay : now.toLocalDate().minusDays(6);
        LocalDate resolvedTo = (toDay != null) ? toDay : now.toLocalDate();
        LocalDateTime trendFrom = resolvedFrom.atStartOfDay();
        LocalDateTime trendTo = resolvedTo.atTime(23, 59, 59);

        String scopedBuildingId = (buildingId == null || buildingId.isBlank()) ? null : buildingId.trim();

        // Parallel DB reads — remote MySQL RTT dominates; wall-clock ≈ max(query)
        CompletableFuture<OccupancyStatsResponse> occupancyF =
                CompletableFuture.supplyAsync(() -> buildOccupancyStats(scopedBuildingId), dashboardExecutor);
        CompletableFuture<SessionStatsResponse> sessionsF =
                CompletableFuture.supplyAsync(
                        () -> buildSessionStats(startOfToday, endOfToday, startOfMonth, now, scopedBuildingId),
                        dashboardExecutor);
        CompletableFuture<ReservationStatsResponse> reservationsF =
                CompletableFuture.supplyAsync(
                        () -> buildReservationStats(startOfToday, endOfToday, scopedBuildingId),
                        dashboardExecutor);
        CompletableFuture<UserStatsResponse> usersF =
                CompletableFuture.supplyAsync(
                        () -> buildUserStats(startOfMonth, now, scopedBuildingId),
                        dashboardExecutor);
        CompletableFuture<IncidentStatsResponse> incidentsF =
                CompletableFuture.supplyAsync(
                        () -> buildIncidentStats(startOfMonth, now, scopedBuildingId),
                        dashboardExecutor);
        CompletableFuture<List<PaymentMethodStatsResponse>> methodsF =
                CompletableFuture.supplyAsync(
                        () -> buildPaymentMethodStats(trendFrom, trendTo, scopedBuildingId),
                        dashboardExecutor);
        CompletableFuture<List<RevenueTrendItem>> trendF =
                CompletableFuture.supplyAsync(
                        () -> buildRevenueTrend(trendFrom, trendTo, scopedBuildingId),
                        dashboardExecutor);
        CompletableFuture<List<RevenueTrendByMethodItem>> trendByMethodF =
                CompletableFuture.supplyAsync(
                        () -> buildRevenueTrendByPaymentMethod(trendFrom, trendTo, scopedBuildingId),
                        dashboardExecutor);

        CompletableFuture.allOf(
                occupancyF, sessionsF, reservationsF, usersF, incidentsF, methodsF, trendF, trendByMethodF).join();

        return DashboardStatsResponse.builder()
                .generatedAt(now)
                .occupancy(occupancyF.join())
                .sessions(sessionsF.join())
                .reservations(reservationsF.join())
                .users(usersF.join())
                .incidents(incidentsF.join())
                .revenueByPaymentMethod(methodsF.join())
                .revenueTrend(trendF.join())
                .revenueTrendByPaymentMethod(trendByMethodF.join())
                .build();
    }

    public DashboardStatsResponse getStats(LocalDate fromDay, LocalDate toDay) {
        return getStats(fromDay, toDay, null);
    }

    private OccupancyStatsResponse buildOccupancyStats(String buildingId) {
        List<BuildingOccupancyCount> rows = parkingSlotRepository.aggregateOccupancyByBuilding();
        if (buildingId != null) {
            rows = rows.stream()
                    .filter(r -> buildingId.equals(r.getBuildingId()))
                    .collect(Collectors.toList());
        }

        long total = 0;
        long available = 0;
        long occupied = 0;
        long reserved = 0;
        long pendingExit = 0;
        List<BuildingOccupancyResponse> buildingList = new ArrayList<>(rows.size());

        for (BuildingOccupancyCount row : rows) {
            long bTotal = row.getTotalSlots();
            long bAvail = row.getAvailableSlots();
            long bOcc = row.getOccupiedSlots();
            long bRes = row.getReservedSlots();
            long bPend = row.getPendingExitSlots();

            total += bTotal;
            available += bAvail;
            occupied += bOcc;
            reserved += bRes;
            pendingExit += bPend;

            double bRate = bTotal > 0
                    ? Math.round((double) (bOcc + bRes) / bTotal * 1000.0) / 10.0
                    : 0.0;
            buildingList.add(BuildingOccupancyResponse.builder()
                    .buildingId(row.getBuildingId())
                    .buildingName(row.getBuildingName())
                    .totalSlots(bTotal)
                    .availableSlots(bAvail)
                    .occupiedSlots(bOcc)
                    .reservedSlots(bRes)
                    .pendingExitSlots(bPend)
                    .occupancyRate(bRate)
                    .build());
        }

        double rate = total > 0
                ? Math.round((double) (occupied + reserved) / total * 1000.0) / 10.0
                : 0.0;
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

    private OccupancyStatsResponse buildOccupancyStats() {
        return buildOccupancyStats(null);
    }

    private SessionStatsResponse buildSessionStats(LocalDateTime startOfToday, LocalDateTime endOfToday,
                                                    LocalDateTime startOfMonth, LocalDateTime now,
                                                    String buildingId) {
        SessionDashboardStats counts = parkingSessionRepository.aggregateDashboardSessionCounts(
                startOfToday, endOfToday, startOfMonth, now, buildingId);
        if (counts == null) {
            counts = new SessionDashboardStats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0, 0.0);
        }

        Double avgDuration = counts.getAvgDurationMinutes();
        Double avgFeeRaw = counts.getAvgFee();
        BigDecimal avgFee = avgFeeRaw != null
                ? BigDecimal.valueOf(avgFeeRaw).setScale(0, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long today = counts.getSessionsToday();
        long guestToday = counts.getGuestSessionsToday();

        return SessionStatsResponse.builder()
                .totalActiveSessions(counts.getActiveSessions())
                .sessionsToday(today)
                .sessionsThisMonth(counts.getSessionsThisMonth())
                .guestSessionsToday(guestToday)
                .registeredSessionsToday(today - guestToday)
                .totalGuestSessionsAllTime(counts.getGuestSessionsAllTime())
                .totalDriverSessionsAllTime(counts.getDriverSessionsAllTime())
                .activeGuestSessions(counts.getActiveGuestSessions())
                .activeDriverSessions(counts.getActiveDriverSessions())
                .avgDurationMinutes(avgDuration != null ? Math.round(avgDuration * 10.0) / 10.0 : 0.0)
                .avgFee(avgFee)
                .build();
    }

    private ReservationStatsResponse buildReservationStats(LocalDateTime startOfToday, LocalDateTime endOfToday,
                                                           String buildingId) {
        Map<String, Long> byStatus = new HashMap<>();
        for (Object[] row : reservationRepository.countGroupedByReservationStatus(buildingId)) {
            if (row[0] != null) {
                byStatus.put(String.valueOf(row[0]), row[1] instanceof Number n ? n.longValue() : 0L);
            }
        }

        return ReservationStatsResponse.builder()
                .totalPending(byStatus.getOrDefault("PENDING", 0L))
                .totalApproved(byStatus.getOrDefault("APPROVED", 0L))
                .totalCompleted(byStatus.getOrDefault("COMPLETED", 0L))
                .totalCancelled(byStatus.getOrDefault("CANCELLED", 0L))
                .totalExpired(byStatus.getOrDefault("EXPIRED", 0L))
                .totalToday(reservationRepository.countReservationsInRange(startOfToday, endOfToday, buildingId))
                .build();
    }

    private UserStatsResponse buildUserStats(LocalDateTime startOfMonth, LocalDateTime now, String buildingId) {
        Map<String, Long> byRole = new HashMap<>();
        for (Object[] row : userRepository.countActiveGroupedByRole()) {
            if (row[0] != null) {
                byRole.put(String.valueOf(row[0]), row[1] instanceof Number n ? n.longValue() : 0L);
            }
        }

        long totalStaff = buildingId != null
                ? buildingStaffRepository.countByBuildingBuildingId(buildingId)
                : byRole.getOrDefault("ROLE_STAFF", 0L);

        return UserStatsResponse.builder()
                .totalDrivers(byRole.getOrDefault("ROLE_DRIVER", 0L))
                .totalStaff(totalStaff)
                .totalManagers(byRole.getOrDefault("ROLE_MANAGER", 0L))
                .newUsersThisMonth(userRepository.countNewUsersInRange(startOfMonth, now))
                .driversCurrentlyParked(parkingSessionRepository.countDistinctDriversCurrentlyParked(buildingId))
                .build();
    }

    private IncidentStatsResponse buildIncidentStats(LocalDateTime startOfMonth, LocalDateTime now,
                                                     String buildingId) {
        IncidentDashboardStats stats = incidentRepository.aggregateDashboardStats(startOfMonth, now, buildingId);
        if (stats == null) {
            stats = new IncidentDashboardStats(0L, 0L, 0L, 0L, 0L);
        }
        return IncidentStatsResponse.builder()
                .totalOpen(stats.getOpenCount())
                .totalInProgress(stats.getInProgressCount())
                .totalResolved(stats.getResolvedCount())
                .totalThisMonth(stats.getThisMonthCount())
                .totalAllTime(stats.getAllTimeCount())
                .build();
    }

    private List<PaymentMethodStatsResponse> buildPaymentMethodStats(
            LocalDateTime from, LocalDateTime to, String buildingId) {
        return paymentRepository.sumRevenueByPaymentMethod(from, to, buildingId).stream()
                .map(p -> PaymentMethodStatsResponse.builder()
                        .method(p.getPaymentMethod())
                        .totalRevenue(p.getTotalRevenue() != null ? p.getTotalRevenue() : BigDecimal.ZERO)
                        .count(p.getCount() != null ? p.getCount() : 0L)
                        .build())
                .collect(Collectors.toList());
    }

    private List<RevenueTrendByMethodItem> buildRevenueTrendByPaymentMethod(
            LocalDateTime from, LocalDateTime to, String buildingId) {
        return paymentRepository.getRevenueTrendByPaymentMethod(from, to, buildingId).stream()
                .map(p -> RevenueTrendByMethodItem.builder()
                        .date(p.getDate())
                        .method(p.getPaymentMethod())
                        .revenue(p.getRevenue() != null ? p.getRevenue() : BigDecimal.ZERO)
                        .count(p.getCount() != null ? p.getCount() : 0L)
                        .build())
                .collect(Collectors.toList());
    }

    private List<RevenueTrendItem> buildRevenueTrend(LocalDateTime from, LocalDateTime to, String buildingId) {
        Map<String, RevenueTrendItem> trendMap = new LinkedHashMap<>();
        for (RevenueTrendProjection p : paymentRepository.getRevenueTrend(from, to, buildingId)) {
            trendMap.put(p.getDate(), RevenueTrendItem.builder()
                    .date(p.getDate())
                    .revenue(p.getRevenue() != null ? p.getRevenue() : BigDecimal.ZERO)
                    .count(p.getCount() != null ? p.getCount() : 0L)
                    .build());
        }

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
