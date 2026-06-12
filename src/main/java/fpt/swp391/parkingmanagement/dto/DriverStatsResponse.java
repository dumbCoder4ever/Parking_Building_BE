package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverStatsResponse {

    private int totalReservations;
    private int totalCompletedSessions;
    private int totalActiveSessions;
    private int totalVehicles;
    private double totalSpent;
    private int totalHoursParked;
    private LocalDateTime firstReservationDate;
    private LocalDateTime lastSessionDate;
    private double averageSessionDurationMinutes;
}
