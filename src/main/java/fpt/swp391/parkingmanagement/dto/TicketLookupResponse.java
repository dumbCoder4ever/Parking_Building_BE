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
@Schema(description = "Ticket lookup result for staff checkout screen.")
public class TicketLookupResponse {

    @Schema(description = "RESERVATION | DRIVER_SESSION | GUEST_SESSION | WALK_IN_DRIVER | NOT_FOUND")
    private String lookupType;

    @Schema(description = "True if session belongs to walk-in driver (no reservation).")
    private Boolean isWalkInDriver;

    @Schema(description = "True if session belongs to guest (no registered user).")
    private Boolean isGuest;

    private ReservationResponse reservation;
    private GuestCheckinResponse guestSession;
    private WalkInDriverInfo walkInDriver;
}
