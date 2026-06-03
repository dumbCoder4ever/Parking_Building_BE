package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ParkingSessionResponse {
    private String sessionId;
    private String ticketCode;
    private String slotId;
    private String slotName;
    private String vehiclePlate;
    private LocalDateTime checkinTime;
}
