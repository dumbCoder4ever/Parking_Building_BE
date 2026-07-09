# Cấu hình & Setup FPT.AI OCR vào Project

Tài liệu này hướng dẫn cấu hình FPT.AI OCR để nhận diện biển số xe Việt Nam trong hệ thống quản lý bãi đỗ xe.

---

## 1. Tổng quan kiến trúc

FPT.AI OCR được dùng cho 2 chức năng chính:
- Nhận diện biển số từ URL ảnh
- Nhận diện biển số từ file upload

Các endpoint sử dụng OCR:
- `POST /api/ocr/plate` — OCR từ URL ảnh
- `POST /api/ocr/plate/upload` — OCR từ upload file
- Quick check-in flow: `POST /api/quick-sessions/driver-checkin`, `POST /api/quick-sessions/guest-checkin`

Luồng xử lý:
1. Client gửi ảnh biển số
2. Controller nhận request và chuyển vào `FptAiOcrService`
3. Service gọi FPT.AI Vision OCR API
4. Hệ thống parse kết quả, trích xuất biển số theo định dạng Việt Nam
5. Trả về `plateNumber`, `confidence`, danh sách `candidates`

---

## 2. Lấy API Key từ FPT.AI

### Bước 1: Đăng ký tài khoản
1. Truy cập https://console.fpt.ai/
2. Đăng ký tài khoản hoặc đăng nhập
3. Vào mục **Vision** → **OCR**

### Bước 2: Tạo API Key
1. Tạo project mới hoặc chọn project có sẵn
2. Chọn dịch vụ **OCR License Plate** hoặc **General OCR**
3. Tạo API Key
4. Copy API Key để cấu hình

### Bước 3: Kiểm tra endpoint đúng
- Endpoint chính thức cho OCR tiếng Việt: `https://api.fpt.ai/vision/ocr/vn`
- Lưu ý đường dẫn có hậu tố `/vn` cho Vietnamese

---

## 3. Cấu hình project

### 3.1 Thêm biến môi trường

Thêm biến môi trường vào file cấu hình của bạn:

```properties
# FPT.AI OCR Configuration
fpt.ai.api.url=https://api.fpt.ai/vision/ocr/vn
fpt.ai.api.key=<YOUR_API_KEY>
fpt.ai.timeout-seconds=30
```

Hoặc đặt vào biến môi trường hệ thống / Railway:

```env
FPT_AI_API_KEY=<YOUR_API_KEY>
```

### 3.2 Kiểm tra file cấu hình

File cấu hình chính: `src/main/resources/application.properties`

```properties
# ==================== FPT.AI OCR ====================
# API Key từ https://fpt.ai/
fpt.ai.api.url=https://api.fpt.ai/vision/ocr/vn
fpt.ai.api.key=${FPT_AI_API_KEY:c0WXiFmRoccngoLy80ndy0JzAhcidoDK}
fpt.ai.timeout-seconds=30
# ===============================================
```

### 3.3 Cấu trúc code OCR

| File | Mô tả |
|------|-------|
| `config/FptAiOcrConfig.java` | Cấu hình `WebClient` bean để gọi FPT.AI API |
| `service/FptAiOcrService.java` | Xử lý OCR: gọi API, parse response, nhận diện biển số VN |
| `controller/OcrController.java` | Expose 2 endpoint: `/api/ocr/plate` và `/api/ocr/plate/upload` |
| `dto/OcrRequest.java` | Request body cho OCR từ URL |
| `dto/OcrResponse.java` | Response trả về kết quả OCR |
| `dto/PlateDuplicateInfo.java` | Thông tin session trùng biển số |

---

## 4. Chạy và test locally

### 4.1 Chạy bằng Gradle

```powershell
.\gradlew.bat bootRun
```

### 4.2 Test OCR từ URL

```powershell
$token = "<JWT_TOKEN>"
$body = @{ imageUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg" } | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/ocr/plate" `
  -Method POST `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $body
```

### 4.3 Test OCR từ file upload

```powershell
$token = "<JWT_TOKEN>"

Invoke-RestMethod -Uri "http://localhost:8080/api/ocr/plate/upload" `
  -Method POST `
  -Headers @{ Authorization = "Bearer $token" } `
  -Form @{ file = Get-Item "C:\Users\Admin\Desktop\plate.jpg" }
```

### 4.4 Response mẫu

```json
{
  "plateNumber": "30A-12345",
  "candidates": ["30A-12345", "30A-123.45"],
  "rawText": "30A-123.45",
  "normalizedText": "30A-123.45",
  "confidence": 0.85
}
```

---

## 5. Kiểm tra nhanh

### 5.1 Verify config đã load

```powershell
curl http://localhost:8080/actuator/configprops | Select-String "fpt.ai"
```

### 5.2 Test không cần auth (nếu endpoint public)

Nếu endpoint không yêu cầu JWT, bạn có thể bỏ header `Authorization`.

### 5.3 Kiểm tra log

Khi OCR chạy, log sẽ hiển thị:
- Request URL/file
- Raw response từ FPT.AI
- Kết quả parse: `plate=30A-12345, confidence=0.85, candidates=[30A-12345, 30A-123.45]`

---

## 6. Quick Check-in flow sử dụng OCR

### 6.1 Driver Quick Check-in

```
POST /api/quick-sessions/driver-checkin
Body: {
  "buildingId": "uuid",
  "plateImage": <multipart file>
}
```

Luồng:
1. Nhận ảnh biển số
2. OCR → lấy `plateNumber`
3. Tìm reservation `PENDING/APPROVED` theo biển số trong building
4. Validate ticket và slot
5. Tạo `ParkingSession` + cập nhật slot, ticket, reservation
6. Trả về thông tin check-in

### 6.2 Guest Quick Check-in

```
POST /api/quick-sessions/guest-checkin
Body: {
  "buildingId": "uuid",
  "vehicleTypeId": "uuid",
  "plateImage": <multipart file>
}
```

Luồng:
1. Nhận ảnh biển số
2. OCR → lấy `plateNumber`
3. Tìm slot trống theo building + vehicleType
4. Tạo `ParkingSession` + guest ticket `G-xxx`
5. Trả về thông tin check-in

---

## 7. Lỗi thường gặp

### 7.1 OCR confidence thấp (< 0.3)

**Nguyên nhân:**
- Ảnh mờ, nghiêng, hoặc ánh sáng kém
- Biển số bị che khuất một phần

**Giải pháp:**
- Chụp ảnh rõ nét, thẳng góc
- Đảm bảo biển số không bị che
- Kiểm tra lại ảnh trước khi gửi lên

### 7.2 Không nhận diện được biển số

**Nguyên nhân:**
- Ảnh không chứa biển số xe
- Biển số xe Việt Nam nhưng định dạng không chuẩn

**Giải pháp:**
- Kiểm tra lại ảnh có đúng biển số không
- Chụp cận biển số hơn
- Kiểm tra log để xem raw text OCR nhận được

### 7.3 FPT.AI API timeout

**Nguyên nhân:**
- Mạng chậm hoặc API FPT.AI phản hồi lâu
- Timeout mặc định là 30 giây

**Giải pháp:**
- Tăng timeout trong config: `fpt.ai.timeout-seconds=60`
- Kiểm tra kết nối mạng

### 7.4 Invalid API Key

**Lỗi:** `401 Unauthorized` từ FPT.AI API

**Giải pháp:**
- Kiểm tra API key đã đúng chưa
- Kiểm tra key còn active trên console FPT.AI
- Regenerate key nếu cần

---

## 8. Triển khai lên Railway

### 8.1 Thêm biến môi trường trên Railway

Trong Railway Dashboard → Project → Backend Service → **Variables**:

```env
FPT_AI_API_KEY=<YOUR_API_KEY>
```

### 8.2 Cấu hình Production

Nếu cần thay đổi endpoint hoặc timeout cho production:

```env
fpt.ai.api.url=https://api.fpt.ai/vision/ocr/vn
fpt.ai.timeout-seconds=30
```

---

## 9. Tùy chỉnh OCR

### 9.1 Thay đổi pattern nhận diện biển số

File: `service/FptAiOcrService.java`

```java
private static final Pattern VN_PLATE_PATTERN =
    Pattern.compile("\\b\\d{2}[A-Z]{1,2}[\\s.\\-]?\\d{3,5}(?:[\\.\\-]?\\d{2})?\\b");
```

### 9.2 Thay đổi confidence threshold

File: `service/ParkingSessionService.java` (quick check-in flows)

```java
if (plateNumber == null || ocr.confidence() < 0.3) {
    throw new BaseAPIException(ErrorCode.OCR_FAILED,
            "Không nhận diện được biển số từ ảnh. Vui lòng chụp lại hoặc nhập tay.");
}
```

Giá trị `0.3` có thể điều chỉnh tùy theo yêu cầu độ chính xác.

### 9.3 Thay đổi timeout

```properties
fpt.ai.timeout-seconds=30
```

---

## 10. Monitoring & Logging

### 10.1 Log OCR

OCR được log ở mức `DEBUG`:

```properties
logging.level.fpt.swp391.parkingmanagement=DEBUG
```

Log sẽ hiển thị:
- Request gửi đến FPT.AI
- Raw response từ FPT.AI
- Kết quả parse và confidence

### 10.2 Monitoring

Nếu cần monitoring, bạn có thể:
- Đếm số request OCR thành công/thất bại
- Theo dõi confidence trung bình
- Đo latency của FPT.AI API

---

## 11. Bảo mật API Key

- **Không commit API key vào Git**
- Sử dụng biến môi trường: `FPT_AI_API_KEY`
- Trong Railway: đặt vào **Variables** section
- Trong local: đặt vào `.env` file (không commit)

```properties
# application.properties
fpt.ai.api.key=${FPT_AI_API_KEY:}
```

---

## 12. So sánh với Tesseract OCR (DEPRECATED)

Project trước đây dùng Tesseract OCR (local), nay đã chuyển sang FPT.AI OCR.

| Tiêu chí | Tesseract (cũ) | FPT.AI (mới) |
|----------|----------------|--------------|
| Chạy | Local binary | API cloud |
| Phụ thuộc | `tess4j`, traineddata | WebClient, API key |
| Độ chính xác | Phụ thuộc traineddata | Cao hơn, pre-trained |
| Bảo trì | Cần cài Tesseract | Không cần |
| Latency | Nhanh (local) | Phụ thuộc mạng |

Tesseract đã được đánh dấu `DEPRECATED` trong config.

---

## 13. Tài liệu tham khảo

- FPT.AI Console: https://console.fpt.ai/
- FPT.AI OCR API Docs: https://docs.fpt.ai/ai/ocr/
- FPT.AI Vision OCR: https://fpt.ai/vision/ocr
- Định dạng biển số VN: https://vi.wikipedia.org/wiki/Biển_số_xe

---

## 14. Troubleshooting nhanh

| Vấn đề | Kiểm tra | Giải pháp |
|--------|----------|-----------|
| 401 Unauthorized | API key đúng không | Kiểm tra key trên console |
| Timeout | Mạng ổn không | Tăng timeout |
| Low confidence | Ảnh rõ không | Chụp lại ảnh |
| Empty response | API URL đúng không | Kiểm tra endpoint |
| Parse lỗi | Response format đổi không | Kiểm tra log raw response |

---

## 15. Liên hệ

Nếu cần hỗ trợ thêm, tham khảo:
- File OCR hiện tại: `docs/OCR_RUN_GUIDE.md`
- FPT.AI Support: support@fpt.ai