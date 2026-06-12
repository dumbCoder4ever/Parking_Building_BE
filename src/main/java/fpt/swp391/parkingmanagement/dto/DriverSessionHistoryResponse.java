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
public class DriverSessionHistoryResponse {

    private Long sessionId;
    private String reservationCode;
    private String buildingName;
    private String floorName;
    private String slotName;
    private String vehiclePlate;
    private String vehicleColor;
    private String vehicleBrand;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Long durationMinutes;
    private String status;
    private Double totalFee;
    private String entryType;
}
