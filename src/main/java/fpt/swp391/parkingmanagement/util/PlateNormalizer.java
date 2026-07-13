package fpt.swp391.parkingmanagement.util;

/**
 * Chuẩn hóa biển số xe về 1 format duy nhất trước khi lưu DB và truy vấn.
 *
 * <p>Mục đích: biển số thực tế có nhiều cách biểu diễn ("54L1 999.99", "54-L199999",
 * "54.L19999", "54L19999"). Nếu lưu DB và query không cùng 1 dạng thì hệ thống
 * sẽ duplicate: cùng 1 xe nhưng DB có 2 plate khác nhau, dẫn đến miss duplicate-check
 * hoặc match sai.</p>
 *
 * <p>Quy tắc:
 * <ol>
 *   <li>Null / blank → trả ""</li>
 *   <li>Uppercase toàn bộ</li>
 *   <li>Strip mọi ký tự không phải chữ/số (khoảng trắng, gạch ngang, chấm, gạch dưới…)</li>
 * </ol>
 * Ví dụ: "54-L1 999.99" → "54L199999", "54.L19999" → "54L19999".
 */
public final class PlateNormalizer {

    private PlateNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.toUpperCase()
                .replaceAll("[^A-Z0-9]", "");
    }
}