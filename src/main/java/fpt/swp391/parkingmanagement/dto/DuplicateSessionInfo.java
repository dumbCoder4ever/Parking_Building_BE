package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Active session info when staff scans a plate that is already checked in.")
public class DuplicateSessionInfo {

    private String sessionId;
    private String ticketCode;
    private String slotName;
    private String zoneName;
    private LocalDateTime checkinTime;
    private String sessionStatus;
    private String lookupType;
}
