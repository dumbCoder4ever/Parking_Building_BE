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

    @Schema(description = "RESERVATION | GUEST_SESSION | WALK_IN_DRIVER | NOT_FOUND")
    private String lookupType;

    private ReservationResponse reservation;
    private GuestCheckinResponse guestSession;
    private WalkInDriverInfo vehicle;
}