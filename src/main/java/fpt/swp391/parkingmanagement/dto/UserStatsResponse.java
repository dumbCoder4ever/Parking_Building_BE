package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {
    private long totalDrivers;
    private long totalStaff;
    private long totalManagers;
    /** Tài khoản mới đăng ký trong tháng hiện tại */
    private long newUsersThisMonth;
    /** Số tài xế đang có xe trong bãi ngay lúc này */
    private long driversCurrentlyParked;
}
