package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.config.VnPayConfig;
import fpt.swp391.parkingmanagement.util.VnPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class VnPayService {

    private final VnPayConfig config;

    public VnPayService(VnPayConfig config) {
        this.config = config;
    }

    /**
     * Tạo URL thanh toán VNPay.
     * Logic port từ ajaxServlet.java: sort params → hashData với URL-encoded value → HMAC-SHA512.
     *
     * @param paymentId  dùng làm vnp_TxnRef (map 1-1 với payment trong DB)
     * @param amount     số tiền VND (sẽ × 100 theo yêu cầu VNPay)
     * @param clientIp   IP của client (lấy từ HttpServletRequest)
     * @param bankCode   null hoặc rỗng = để VNPay tự chọn; "VNPAYQR","VNBANK","INTCARD"
     * @param language   "vn" hoặc "en"
     */
    public String createPaymentUrl(String paymentId, BigDecimal amount,
                                   String clientIp, String bankCode, String language) {
        // Timezone GMT+7 giống JSP sample
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");

        String vnpCreateDate = formatter.format(cld.getTime());
        cld.add(Calendar.MINUTE, 15);
        String vnpExpireDate = formatter.format(cld.getTime());

        long vnpAmount = amount.multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", config.getVersion());
        vnpParams.put("vnp_Command", config.getCommand());
        vnpParams.put("vnp_TmnCode", config.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", config.getCurrCode());
        vnpParams.put("vnp_TxnRef", paymentId);
        vnpParams.put("vnp_OrderInfo", "Thanh toan phi do xe:" + paymentId);
        vnpParams.put("vnp_OrderType", config.getOrderType());
        vnpParams.put("vnp_Locale", (language != null && !language.isEmpty()) ? language : config.getLocale());
        vnpParams.put("vnp_ReturnUrl", config.getReturnUrl());
        vnpParams.put("vnp_IpnUrl", config.getIpnUrl());
        vnpParams.put("vnp_IpAddr", clientIp);
        vnpParams.put("vnp_CreateDate", vnpCreateDate);
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);

        if (bankCode != null && !bankCode.isEmpty()) {
            vnpParams.put("vnp_BankCode", bankCode);
        }

        VnPayUtil.BuildResult built = VnPayUtil.buildQueryAndHash(vnpParams);
        String secureHash = VnPayUtil.hmacSHA512(config.getHashSecret(), built.hashData);
        return config.getPayUrl() + "?" + built.queryUrl + "&vnp_SecureHash=" + secureHash;
    }

    /** Overload không cần bankCode/language */
    public String createPaymentUrl(String paymentId, BigDecimal amount, String clientIp) {
        return createPaymentUrl(paymentId, amount, clientIp, null, null);
    }

    /**
     * Verify chữ ký callback từ VNPay (dùng cho cả IPN lẫn Return URL).
     * Port từ vnpay_ipn.jsp: URL-encode cả name và value trước khi hash.
     */
    public boolean verifySignature(HttpServletRequest request) {
        return VnPayUtil.verifySignature(request, config.getHashSecret());
    }
}
