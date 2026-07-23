package fpt.swp391.parkingmanagement.repository;

/**
 * One-scan aggregate for manager/admin dashboard incident counters.
 * SYSTEM auto-incidents are excluded at query time.
 */
public class IncidentDashboardStats {

    private final Long openCount;
    private final Long inProgressCount;
    private final Long resolvedCount;
    private final Long thisMonthCount;
    private final Long allTimeCount;

    public IncidentDashboardStats(
            Long openCount,
            Long inProgressCount,
            Long resolvedCount,
            Long thisMonthCount,
            Long allTimeCount) {
        this.openCount = openCount;
        this.inProgressCount = inProgressCount;
        this.resolvedCount = resolvedCount;
        this.thisMonthCount = thisMonthCount;
        this.allTimeCount = allTimeCount;
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }

    public long getOpenCount() {
        return nz(openCount);
    }

    public long getInProgressCount() {
        return nz(inProgressCount);
    }

    public long getResolvedCount() {
        return nz(resolvedCount);
    }

    public long getThisMonthCount() {
        return nz(thisMonthCount);
    }

    public long getAllTimeCount() {
        return nz(allTimeCount);
    }
}
