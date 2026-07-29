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
@Schema(description = "Plate -> ticketCode resolver. Step 1 cho staff checkout flow: " +
        "sau khi staff quet bien so, lay ticketCode roi goi lookupByTicketCode.")
public class PlateTicketCodeResponse {

    @Schema(description = "true neu tim thay ticketCode cho plate, false neu khong co session ACTIVE/PENDING_PAYMENT.")
    private boolean found;

    @Schema(description = "ticketCode cua session ACTIVE/PENDING_PAYMENT gan nhat. Null neu khong tim thay.")
    private String ticketCode;

    @Schema(description = "sessionId cua session tuong ung. Null neu khong tim thay.")
    private String sessionId;

    @Schema(description = "Loai session: RESERVATION (qua dat cho), GUEST (walk-in khong co user), WALK_IN_DRIVER (walk-in co user). Null neu khong tim thay.")
    private String lookupType;
}