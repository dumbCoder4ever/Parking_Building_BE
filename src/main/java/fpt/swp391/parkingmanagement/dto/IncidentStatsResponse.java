package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentStatsResponse {
    /** Sự cố đang mở, chưa xử lý */
    private long totalOpen;
    /** Sự cố phát sinh trong tháng này */
    private long totalThisMonth;
    /** Tổng tất cả thời gian */
    private long totalAllTime;
}
