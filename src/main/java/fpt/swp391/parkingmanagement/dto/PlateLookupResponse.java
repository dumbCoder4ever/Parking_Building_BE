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
@Schema(description = "Plate lookup result for staff check-in / checkout screen.")
public class PlateLookupResponse {

    @Schema(description = "RESERVATION | WALK_IN_DRIVER | GUEST_SESSION | ALREADY_CHECKED_IN | ALREADY_CHECKED_OUT | NOT_FOUND")
    private String lookupType;

    private ReservationResponse reservation;
    private GuestCheckinResponse guestSession;
    private WalkInDriverInfo walkInDriver;
    private WalkInDriverInfo vehicle;
    private Boolean isWalkInDriver;
    private Boolean isGuest;
    private DuplicateSessionInfo duplicateActiveSession;
}
