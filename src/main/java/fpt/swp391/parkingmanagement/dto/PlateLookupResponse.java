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
@Schema(description = "Kết quả tra cứu biển số cho màn staff check-in. Ưu tiên reservation PENDING trước guest session.")
public class PlateLookupResponse {

    @Schema(description = "RESERVATION | GUEST_SESSION | NOT_FOUND")
    private String lookupType;

    private ReservationResponse reservation;
    private GuestCheckinResponse guestSession;
}
