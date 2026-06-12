package fpt.swp391.parkingmanagement.util;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Port trực tiếp từ Config.java của VNPay JSP sample.
 * Logic hash phải khớp chính xác: sort key → fieldName=URLencode(fieldValue) → HMAC-SHA512.
 */
public class VnPayUtil {

    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) throw new NullPointerException();
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes();
            final SecretKeySpec secretKeySpec = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKeySpec);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * Build hashData và queryUrl từ params (đã sort).
     * hashData: fieldName=URLencode(value) ghép bằng &
     * queryUrl: URLencode(fieldName)=URLencode(value) ghép bằng &
     */
    public static BuildResult buildQueryAndHash(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();

        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName)
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII))
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        return new BuildResult(hashData.toString(), query.toString());
    }

    /**
     * Verify checksum từ callback của VNPay.
     * Port từ vnpay_ipn.jsp: URL-encode value của từng param, sort by key, HMAC-SHA512.
     * Tên param vnp_* đều là ASCII thuần → URLEncoder không thay đổi tên.
     */
    public static boolean verifySignature(HttpServletRequest request, String secretKey) {
        String vnpSecureHash = request.getParameter("vnp_SecureHash");
        if (vnpSecureHash == null || vnpSecureHash.isEmpty()) return false;

        Map<String, String> fields = new HashMap<>();
        for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements(); ) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                // URL-encode value giống JSP sample
                fields.put(fieldName, URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
            }
        }
        fields.remove("vnp_SecureHashType");
        fields.remove("vnp_SecureHash");

        String signValue = hashAllFields(fields, secretKey);
        return signValue.equalsIgnoreCase(vnpSecureHash);
    }

    /** hashAllFields: giống Config.hashAllFields trong JSP sample */
    public static String hashAllFields(Map<String, String> fields, String secretKey) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                sb.append(fieldName).append("=").append(fieldValue);
            }
            if (itr.hasNext()) sb.append("&");
        }
        return hmacSHA512(secretKey, sb.toString());
    }

    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-FORWARDED-FOR");
        if (ip == null) ip = request.getRemoteAddr();
        return ip;
    }

    public static class BuildResult {
        public final String hashData;
        public final String queryUrl;

        public BuildResult(String hashData, String queryUrl) {
            this.hashData = hashData;
            this.queryUrl = queryUrl;
        }
    }
}
