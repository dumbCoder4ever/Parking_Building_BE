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
@Schema(description = "Kết quả tra cứu biển số cho màn staff check-in. "
        + "Ưu tiên reservation PENDING -> active guest session -> driver đã đăng ký xe (WALK_IN_DRIVER).")
public class PlateLookupResponse {

    @Schema(description = "RESERVATION | GUEST_SESSION | WALK_IN_DRIVER | NOT_FOUND")
    private String lookupType;

    private ReservationResponse reservation;
    private GuestCheckinResponse guestSession;
    private WalkInDriverInfo vehicle;
}