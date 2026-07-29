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
@Schema(description = "Plate lookup result for staff check-in screen. Prioritizes PENDING reservation before guest session.")
public class PlateLookupResponse {

    @Schema(description = "RESERVATION | DRIVER_SESSION | GUEST_SESSION | WALK_IN_DRIVER | ALREADY_PARKED | NOT_FOUND")
    private String lookupType;

    private ReservationResponse reservation;
    private GuestCheckinResponse guestSession;

    @Schema(description = "Thong tin walk-in driver khi lookupType = WALK_IN_DRIVER va da co session ACTIVE. " +
            "Tuong duong guestSession nhung danh cho driver da dang ky xe.")
    private WalkInDriverInfo walkInDriver;

    @Schema(description = "Thong tin walk-in driver khi lookupType = WALK_IN_DRIVER va CHUA co session (moi dang ky xe, chua check-in).")
    private WalkInDriverInfo vehicle;

    @Schema(description = "True neu lookupType = WALK_IN_DRIVER (de FE render UI phu hop).")
    private Boolean isWalkInDriver;

    @Schema(description = "True neu lookupType = GUEST_SESSION (de FE render UI phu hop).")
    private Boolean isGuest;

    @Schema(description = "Khi lookupType = ALREADY_PARKED, chứa thông tin session đang ACTIVE/PENDING_PAYMENT "
            + "cho biển số đã scan. Null khi lookupType khác.")
    private DuplicateSessionInfo duplicateActiveSession;
}
