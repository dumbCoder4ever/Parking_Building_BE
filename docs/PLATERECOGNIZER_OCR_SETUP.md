# PlateRecognizer OCR — Setup & Run Guide

## Tổng quan

Hệ thống dùng **PlateRecognizer** (Cloud API) để nhận diện biển số xe Việt Nam. Thay thế cho FPT.AI / Tesseract cũ.

---

## 1. Kiến trúc OCR

```
Staff chụp ảnh biển số
         ↓
   OcrController
   ├── POST /api/ocr/plate          → nhận URL ảnh
   └── POST /api/ocr/plate/upload   → nhận file upload (DÙNG CHÍNH)
         ↓
   PlateRecognizerService
   ├── recognizeFromUrl()
   └── recognizeFromUpload()          ← dùng cho checkin/checkout
         ↓
   PlateRecognizer Cloud API
   https://api.platerecognizer.com/v1/plate-reader/
         ↓
   OcrResult(plateNumber, confidence, candidates, rawText, normalizedText)
```

### Các endpoint dùng OCR

| Endpoint | Method | Mô tả |
|---|---|---|
| `/api/ocr/plate` | POST JSON | OCR từ imageUrl |
| `/api/ocr/plate/upload` | POST multipart | OCR từ file upload (**DÙNG CHÍNH**) |
| `/api/sessions/quick-checkin` | POST multipart | Quick checkin tự động (DRIVER/GUEST) |
| `/api/sessions/guest/checkin/ocr` | POST multipart | Guest checkin bằng OCR |
| `/api/sessions/guest/checkout/ocr` | POST multipart | Guest checkout bằng OCR |

---

## 2. Cấu hình

### 2.1 Trong `application.properties`

```properties
# ==================== PlateRecognizer OCR ====================
platerecognizer.api.url=https://api.platerecognizer.com/v1/plate-reader/
platerecognizer.api.key=<YOUR_API_KEY>
platerecognizer.regions=vn
platerecognizer.timeout-seconds=30
# ===========================================================
```

### 2.2 Trên Railway (Environment Variables)

```env
platerecognizer.api.key=<YOUR_API_KEY>
```

---

## 3. Lấy API Key PlateRecognizer

### Bước 1: Đăng ký
1. Truy cập https://platerecognizer.com/
2. Đăng ký tài khoản (dùng email)

### Bước 2: Chọn plan
- **Free tier**: 500 requests/month (đủ để dev/test)
- **Paid**: $19/month → 5,000 requests

### Bước 3: Lấy API Key
1. Login → Dashboard → **Account Settings** → **API Key**
2. Copy key (dạng: `1041fafc50a06c07b298e28525240d7581b9fd3f`)
3. Paste vào `application.properties` hoặc Railway Variables

### Bước 4: Test thử
```powershell
curl -X POST https://api.platerecognizer.com/v1/plate-reader/ \
  -H "Authorization: Token 1041fafc50a06c07b298e28525240d7581b9fd3f" \
  -F "upload=@C:\path\to\plate.jpg"
```

---

## 4. Chạy app

### Local (PowerShell)
```powershell
cd C:\Users\Admin\Downloads\BE\Parking_Building_BE
.\gradlew.bat bootRun
```

App chạy tại: **http://localhost:8080**

Swagger UI: **http://localhost:8080/swagger-ui.html**

---

## 5. Test OCR bằng Postman / Swagger

### 5.1 Test OCR Upload (cách test nhanh nhất)

**POST** `http://localhost:8080/api/ocr/plate/upload`

- **Content-Type**: `multipart/form-data`
- **Body**: `file` = ảnh biển số (JPEG/PNG)

Response thành công:
```json
{
  "plateNumber": "30A-12345",
  "candidates": ["30A-12345"],
  "rawText": "30A 12345",
  "normalizedText": "30A-12345",
  "confidence": 0.92,
  "duplicateActiveSession": null
}
```

### 5.2 Test OCR từ URL

**POST** `http://localhost:8080/api/ocr/plate`

```json
{
  "imageUrl": "https://your-cloudinary-url.com/plate.jpg"
}
```

---

## 6. Test flow đầy đủ

### 6.1 Quick Check-in Driver (OCR)

Staff quét ảnh → hệ thống tự tìm reservation → tạo session.

**POST** `http://localhost:8080/api/sessions/quick-checkin`

- **Content-Type**: `multipart/form-data`
- **Authorization**: Bearer `<staff-token>`

| Field | Bắt buộc | Mô tả |
|---|---|---|
| `plateImage` | **Có** | Ảnh biển số (JPEG/PNG) |
| `buildingId` | **Có** | Building staff đang làm việc |
| `mode` | Không | `DRIVER` (mặc định) |

Response:
```json
{
  "success": true,
  "data": {
    "checkinType": "DRIVER",
    "ticketCode": "TKT-1234567890-456",
    "sessionId": "abc123-def456",
    "plateNumber": "30A-12345",
    "ocrConfidence": 0.92,
    "slotName": "A-001",
    "checkinTime": "2026-07-10T22:00:00",
    "estimatedFee": 5000
  }
}
```

### 6.2 Quick Check-in Guest (OCR)

Staff quét ảnh → hệ thống auto-assign slot → tạo session vãng lai.

**POST** `http://localhost:8080/api/sessions/quick-checkin`

| Field | Bắt buộc | Mô tả |
|---|---|---|
| `plateImage` | **Có** | Ảnh biển số |
| `buildingId` | **Có** | Building |
| `vehicleTypeId` | **Có** | Loại xe (MOTORCYCLE/CAR) |
| `mode` | Không | `GUEST` |

Response:
```json
{
  "success": true,
  "data": {
    "checkinType": "GUEST",
    "ticketCode": "G-1751659789123-4827",
    "sessionId": "xyz789-abc123",
    "plateNumber": "51H-99999",
    "ocrConfidence": 0.88,
    "slotName": "B-003",
    "checkinTime": "2026-07-10T22:00:00",
    "estimatedFee": 5000
  }
}
```

### 6.3 Guest Check-in OCR (endpoint riêng)

**POST** `http://localhost:8080/api/sessions/guest/checkin/ocr`

- **Content-Type**: `multipart/form-data`
- **Authorization**: Bearer `<staff-token>`

| Field | Bắt buộc | Mô tả |
|---|---|---|
| `plateImage` | **Có** | Ảnh biển số |
| `buildingId` | **Có** | Building |
| `vehicleTypeId` | **Có** | Loại xe |
| `vehicleColor` | Không | Màu xe |
| `brand` | Không | Hãng xe |
| `model` | Không | Dòng xe |
| `guestName` | Không | Tên khách |
| `guestPhone` | Không | SĐT khách |
| `note` | Không | Ghi chú |
| `checkinImage` | Không | Ảnh check-in |

### 6.4 Guest Checkout OCR (endpoint riêng)

**POST** `http://localhost:8080/api/sessions/guest/checkout/ocr`

- **Content-Type**: `multipart/form-data`
- **Authorization**: Bearer `<staff-token>`

| Field | Bắt buộc | Mô tả |
|---|---|---|
| `plateImage` | **Có** | Ảnh biển số lúc xe ra |
| `ticketCode` | **Có** | Mã vé (G-xxx) |
| `paymentMethod` | Không | `CASH` (mặc định), `VNPAY`, `PAYOS`, `MOMO` |
| `checkoutImage` | Không | Ảnh check-out |

---

## 7. Ràng buộc & Lỗi thường gặp

### 7.1 Ràng buộc biển số

| Lỗi | Nguyên nhân | Xử lý |
|---|---|---|
| `PLATE_MISMATCH` | Biển số quét không khớp biển số đăng ký | Staff kiểm tra xe thực tế |
| `PLATE_ALREADY_PARKED` | Biển số đã có session ACTIVE | Checkout xe đó trước |
| `OCR_FAILED` | Ảnh mờ/không đọc được | Chụp lại ảnh rõ hơn |
| `SLOT_NOT_AVAILABLE` | Không còn slot trống | Chọn building/loại xe khác |
| `RESERVATION_NOT_FOUND` | Không tìm thấy reservation | Driver chưa đặt chỗ, chuyển GUEST mode |

### 7.2 Confidence threshold

- `confidence < 0.3` → hệ thống từ chối, yêu cầu chụp lại
- `confidence 0.3 - 0.7` → cảnh báo, vẫn cho phép
- `confidence > 0.7` → OK

### 7.3 Bảo mật API Key

```
⚠️ KHÔNG commit API key vào Git!
```

Trong `.gitignore` đã có:
```
# Environment / secrets
*.env
application-local.properties
```

API key trong `application.properties` hiện tại là key test — thay bằng key thật khi deploy.

---

## 8. Monitoring

### 8.1 Log OCR

Trong `application.properties`:
```properties
logging.level.fpt.swp391.parkingmanagement.service.PlateRecognizerService=DEBUG
```

Log sẽ hiển thị:
- File ảnh / URL được OCR
- Raw response từ PlateRecognizer
- Kết quả parse: plate, confidence, candidates

### 8.2 Kiểm tra nhanh

```powershell
# Xem logs OCR realtime
.\gradlew.bat bootRun 2>&1 | Select-String "OCR\|PlateRecognizer\|plateNumber"
```

---

## 9. Thay đổi OCR Provider

Nếu muốn đổi sang FPT.AI hoặc OCR.space:

1. Viết service mới implement cùng interface `OcrService`
2. Thay `PlateRecognizerService` bean trong config
3. Không cần sửa controller/service business logic

```java
public interface OcrService {
    OcrResult recognizeFromUpload(byte[] imageBytes, String filename);
    OcrResult recognizeFromUrl(String imageUrl);
}
```

---

## 10. Triển khai Railway

### Variables cần thiết

| Variable | Giá trị |
|---|---|
| `platerecognizer.api.key` | Key từ platerecognizer.com |
| `JDBC_DATABASE_URL` | MySQL connection string |
| `JWT_SECRET` | Secret key cho JWT |
| `CLOUDINARY_URL` | Cloudinary config |

### Health check

```bash
curl https://your-app.railway.app/actuator/health
```
