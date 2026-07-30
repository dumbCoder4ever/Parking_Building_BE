package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Plate -> ticketCode resolver for staff checkout flow.")
public class PlateTicketCodeResponse {

    @Schema(description = "true if ticketCode found for ACTIVE/PENDING_PAYMENT session on plate.")
    private boolean found;

    @Schema(description = "ticketCode of latest ACTIVE/PENDING_PAYMENT session. Null if not found.")
    private String ticketCode;

    @Schema(description = "sessionId of matching session. Null if not found.")
    private String sessionId;

    @Schema(description = "RESERVATION | GUEST | WALK_IN_DRIVER. Null if not found.")
    private String lookupType;
}
