package fpt.swp391.parkingmanagement.repository;

/**
 * One-scan aggregate for admin dashboard session counters + averages.
 */
public class SessionDashboardStats {

    private final Long activeSessions;
    private final Long sessionsToday;
    private final Long sessionsThisMonth;
    private final Long guestSessionsToday;
    private final Long guestSessionsAllTime;
    private final Long driverSessionsAllTime;
    private final Long activeGuestSessions;
    private final Long activeDriverSessions;
    private final Double avgDurationMinutes;
    private final Double avgFee;

    public SessionDashboardStats(
            Long activeSessions,
            Long sessionsToday,
            Long sessionsThisMonth,
            Long guestSessionsToday,
            Long guestSessionsAllTime,
            Long driverSessionsAllTime,
            Long activeGuestSessions,
            Long activeDriverSessions,
            Double avgDurationMinutes,
            Double avgFee) {
        this.activeSessions = activeSessions;
        this.sessionsToday = sessionsToday;
        this.sessionsThisMonth = sessionsThisMonth;
        this.guestSessionsToday = guestSessionsToday;
        this.guestSessionsAllTime = guestSessionsAllTime;
        this.driverSessionsAllTime = driverSessionsAllTime;
        this.activeGuestSessions = activeGuestSessions;
        this.activeDriverSessions = activeDriverSessions;
        this.avgDurationMinutes = avgDurationMinutes;
        this.avgFee = avgFee;
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }

    public long getActiveSessions() {
        return nz(activeSessions);
    }

    public long getSessionsToday() {
        return nz(sessionsToday);
    }

    public long getSessionsThisMonth() {
        return nz(sessionsThisMonth);
    }

    public long getGuestSessionsToday() {
        return nz(guestSessionsToday);
    }

    public long getGuestSessionsAllTime() {
        return nz(guestSessionsAllTime);
    }

    public long getDriverSessionsAllTime() {
        return nz(driverSessionsAllTime);
    }

    public long getActiveGuestSessions() {
        return nz(activeGuestSessions);
    }

    public long getActiveDriverSessions() {
        return nz(activeDriverSessions);
    }

    public Double getAvgDurationMinutes() {
        return avgDurationMinutes;
    }

    public Double getAvgFee() {
        return avgFee;
    }
}
