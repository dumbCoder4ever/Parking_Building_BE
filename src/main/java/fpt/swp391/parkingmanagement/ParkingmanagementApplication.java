package fpt.swp391.parkingmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableAsync
public class ParkingmanagementApplication {

	public static void main(String[] args) {
		String timezone = System.getenv("APP_TIMEZONE");
		if (timezone == null || timezone.isBlank()) {
			timezone = "Asia/Ho_Chi_Minh";
		}
		TimeZone.setDefault(TimeZone.getTimeZone(timezone));
		SpringApplication.run(ParkingmanagementApplication.class, args);
	}

}
