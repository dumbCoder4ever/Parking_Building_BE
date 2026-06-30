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
public class PaymentMethodStatsResponse {
    /** Phương thức thanh toán: CASH, VNPAY, PAYOS, MOMO */
    private String method;
    private BigDecimal totalRevenue;
    private long count;
}
