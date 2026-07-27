package fpt.swp391.parkingmanagement.config;

import fpt.swp391.parkingmanagement.util.AppClock;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
@Slf4j
public class AppTimezoneConfig {

    @Value("${app.timezone:Asia/Ho_Chi_Minh}")
    private String appTimezone;

    @PostConstruct
    void configureTimezone() {
        ZoneId zoneId = ZoneId.of(appTimezone);
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        AppClock.setZoneId(zoneId);
        log.info("Application timezone configured: {} (JVM default + AppClock)", appTimezone);
    }
}
