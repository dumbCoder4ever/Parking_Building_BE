package fpt.swp391.parkingmanagement.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class ParkingConfig {

    @Value("${parking.grace-period-minutes:30}")
    private int gracePeriodMinutes;

    @Value("${parking.payment-reminder-minutes:15}")
    private int paymentReminderMinutes;

    @Value("${parking.max-parking-hours:24}")
    private int maxParkingHours;

    @Value("${parking.peak-hour-stddev-factor:1.0}")
    private double peakHourStddevFactor;

    @Value("${parking.overstay-notify-enabled:true}")
    private boolean overstayNotifyEnabled;
}
