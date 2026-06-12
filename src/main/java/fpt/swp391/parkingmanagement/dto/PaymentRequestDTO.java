package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import fpt.swp391.parkingmanagement.enums.PaymentMethod;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestDTO {
    private String sessionId;
    private String paymentMethod;
    private BigDecimal amount;
    private String transactionCode;
    private String note;
    private String driverId;
    // VNPay specific (optional)
    private String bankCode;  // null = để VNPay tự chọn; hoặc "VNPAYQR","VNBANK","INTCARD"
    private String language;  // "vn" hoặc "en" (mặc định "vn")
}
