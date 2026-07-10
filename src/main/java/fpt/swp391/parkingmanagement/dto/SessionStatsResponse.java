package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatsResponse {
    /** Số session đang hoạt động ngay lúc này */
    private long totalActiveSessions;
    /** Tổng số lượt vào bãi hôm nay */
    private long sessionsToday;
    /** Tổng số lượt vào bãi tháng này */
    private long sessionsThisMonth;
    /** Session khách vãng lai hôm nay (không có reservation) */
    private long guestSessionsToday;
    /** Session user đã đăng ký hôm nay */
    private long registeredSessionsToday;
    /** Tổng lượt guest check-in từ trước đến nay */
    private long totalGuestSessionsAllTime;
    /** Tổng lượt driver check-in từ trước đến nay */
    private long totalDriverSessionsAllTime;
    /** Guest đang đỗ xe ngay lúc này */
    private long activeGuestSessions;
    /** Driver đang đỗ xe ngay lúc này */
    private long activeDriverSessions;
    /** Thời gian đỗ xe trung bình (phút) các session đã hoàn thành */
    private double avgDurationMinutes;
    /** Phí trung bình mỗi lần đỗ các session đã hoàn thành */
    private BigDecimal avgFee;
}
