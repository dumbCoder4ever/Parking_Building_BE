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
public class RevenueTrendByMethodItem {
    /** Ngày dạng yyyy-MM-dd */
    private String date;
    /** CASH, VNPAY, PAYOS, MOMO, ... */
    private String method;
    private BigDecimal revenue;
    private long count;
}
