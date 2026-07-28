package fpt.swp391.parkingmanagement.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Application clock in configured business timezone (default: Asia/Ho_Chi_Minh).
 * Use instead of {@link LocalDateTime#now()} for user-facing timestamps.
 */
public final class AppClock {

    private static volatile ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");

    private AppClock() {
    }

    public static void setZoneId(ZoneId zone) {
        zoneId = zone;
    }

    public static ZoneId zone() {
        return zoneId;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(zoneId);
    }
}
