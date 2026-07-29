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
@Schema(description = "Thông tin session ACTIVE/PENDING_PAYMENT đã tồn tại cho biển số scan lại. "
        + "FE dùng để cảnh báo staff biển đã đỗ và điều hướng sang màn checkout.")
public class DuplicateSessionInfo {

    private String sessionId;
    private String ticketCode;
    private String slotName;
    private String zoneName;
    private LocalDateTime checkinTime;
    private String sessionStatus;
    /** GUEST_SESSION | DRIVER_SESSION | WALK_IN_DRIVER — loại session đang active. */
    private String lookupType;
}
