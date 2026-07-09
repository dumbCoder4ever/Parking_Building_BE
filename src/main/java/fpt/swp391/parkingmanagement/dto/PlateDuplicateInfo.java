package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

public record PlateDuplicateInfo(
        String plateNumber,
        String sessionId,
        String ticketCode,
        LocalDateTime checkinTime,
        boolean isGuest,
        String buildingName,
        String slotName
) {
}
