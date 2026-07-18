package fpt.swp391.parkingmanagement.repository;

/**
 * One-scan aggregate for admin dashboard incident counters.
 */
public class IncidentDashboardStats {

    private final Long openCount;
    private final Long thisMonthCount;
    private final Long allTimeCount;

    public IncidentDashboardStats(Long openCount, Long thisMonthCount, Long allTimeCount) {
        this.openCount = openCount;
        this.thisMonthCount = thisMonthCount;
        this.allTimeCount = allTimeCount;
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }

    public long getOpenCount() {
        return nz(openCount);
    }

    public long getThisMonthCount() {
        return nz(thisMonthCount);
    }

    public long getAllTimeCount() {
        return nz(allTimeCount);
    }
}
