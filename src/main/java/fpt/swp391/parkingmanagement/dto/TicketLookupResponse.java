package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả tra cứu theo ticketCode trước khi staff checkout.
 * Phân biệt rõ giữa các loại session để FE hiển thị UI phù hợp.
 *
 * <ul>
 *   <li>{@code RESERVATION}: driver có đặt chỗ trước (có reservation).</li>
 *   <li>{@code DRIVER_SESSION}: driver đã check-in từ reservation, đang trong bãi.</li>
 *   <li>{@code GUEST_SESSION}: khách vãng lai (không phải driver walk-in).</li>
 *   <li>{@code WALK_IN_DRIVER}: driver walk-in (đã đăng ký xe nhưng không qua reservation).</li>
 *   <li>{@code NOT_FOUND}: không tìm thấy.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ticket lookup result for staff checkout screen.")
public class TicketLookupResponse {

    @Schema(description = "RESERVATION | DRIVER_SESSION | GUEST_SESSION | WALK_IN_DRIVER | NOT_FOUND")
    private String lookupType;

    @Schema(description = "True nếu session thuộc về driver walk-in (không có reservation). "
            + "FE dùng để hiển thị label 'Walk-in Driver' và logic phù hợp.")
    private Boolean isWalkInDriver;

    @Schema(description = "True nếu session thuộc về guest (không phải driver đăng ký).")
    private Boolean isGuest;

    private ReservationResponse reservation;
    private GuestCheckinResponse guestSession;
    private WalkInDriverInfo walkInDriver;
}