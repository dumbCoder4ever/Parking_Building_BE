package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationStatsResponse {
    /** Đang chờ staff duyệt */
    private long totalPending;
    /** Đã duyệt, chưa check-in */
    private long totalApproved;
    /** Đã hoàn thành */
    private long totalCompleted;
    /** Đã hủy */
    private long totalCancelled;
    /** Hết hạn (không check-in đúng giờ) */
    private long totalExpired;
    /** Số lượt đặt chỗ được tạo hôm nay */
    private long totalToday;
}
