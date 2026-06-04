package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ReservationResponse {
    private String reservationId;
    private String reservationCode;
    private LocalDateTime reservationStart;
    private LocalDateTime reservationEnd;
    private String slotId;
    private String slotName;
    private String ticketCode;
    private String qrCode;
}
