# Hướng dẫn setup Payment (PayOS / VNPay) — Local dev

Tài liệu này mô tả các bước cần thiết để chạy và test thanh toán trên môi trường local.

---

## 1. Yêu cầu

| Thành phần | Ghi chú |
|---|---|
| Java 17 | Theo `build.gradle` |
| MySQL | Database `parking_db` |
| Spring Boot app | Chạy port `8080` (mặc định) |
| [ngrok](https://ngrok.com/) | Expose local ra HTTPS public URL |
| Tài khoản [PayOS](https://my.payos.vn) | Lấy `client-id`, `api-key`, `checksum-key` |
| (Tuỳ chọn) Tài khoản [VNPay Sandbox](https://sandbox.vnpayment.vn/) | Nếu test VNPay |

Dependency PayOS trong project:

```gradle
implementation 'vn.payos:payos-java:2.0.1'
```

---

## 2. Cấu hình database

Nếu bảng `payments` được tạo từ schema cũ (thiếu `PAYOS`, `VNPAY` hoặc `PENDING`), chạy migration:

```bash
mysql -u root -p parking_db < docs/db/alter_payments_add_payos.sql
```

Hoặc chạy trực tiếp trong MySQL Workbench / DBeaver:

```sql
USE parking_db;

ALTER TABLE payments
    MODIFY COLUMN payment_method ENUM('CASH', 'BANKING', 'MOMO', 'VNPAY', 'PAYOS') NOT NULL;

ALTER TABLE payments
    MODIFY COLUMN payment_status ENUM('SUCCESS', 'FAILED', 'PENDING') DEFAULT 'SUCCESS';
```

Kiểm tra:

```sql
SHOW COLUMNS FROM payments LIKE 'payment_method';
SHOW COLUMNS FROM payments LIKE 'payment_status';
```

---

## 3. Cấu hình `application.properties`

File: `src/main/resources/application.properties`

### PayOS

```properties
payos.client-id=<your-client-id>
payos.api-key=<your-api-key>
payos.checksum-key=<your-checksum-key>
payos.log-level=NONE
payos.return-url=https://<ngrok-url>/api/payments/payos/return
payos.cancel-url=https://<ngrok-url>/api/payments/payos/cancel
```

### VNPay (nếu dùng)

```properties
vnpay.tmn-code=<your-tmn-code>
vnpay.hash-secret=<your-hash-secret>
vnpay.return-url=https://<ngrok-url>/api/payments/vnpay/return
vnpay.ipn-url=https://<ngrok-url>/api/payments/vnpay/ipn
```

> **Lưu ý:** Không commit key thật lên git public. Dùng biến môi trường hoặc file local không push.

---

## 4. Setup ngrok

PayOS/VNPay cần URL **HTTPS public** để:
- Redirect trình duyệt sau thanh toán (`return-url`, `cancel-url`)
- Gọi webhook server-to-server (`/api/payments/payos/webhook`)

`localhost` **không dùng được** cho các callback này.

### Bước chạy

```bash
# Terminal 1: chạy backend
.\gradlew.bat bootRun

# Terminal 2: chạy ngrok (giữ terminal này mở suốt khi test)
ngrok http 8080
```

Copy URL dạng `https://xxxx.ngrok-free.dev` từ ngrok.

### Cập nhật URL sau khi bật ngrok

1. Sửa `payos.return-url`, `payos.cancel-url` (và `vnpay.*` nếu có) trong `application.properties`
2. **Restart backend** — URL được gắn vào payment link lúc tạo, nên phải restart trước khi `initiate`
3. Đăng ký lại webhook (mục 5)

> URL ngrok free **đổi mỗi lần** tắt/bật lại ngrok. Mỗi lần đổi URL phải lặp lại 3 bước trên.

### Lỗi thường gặp

| Lỗi | Nguyên nhân |
|---|---|
| `ERR_NGROK_3200 - endpoint is offline` | ngrok tắt hoặc URL cũ trong `application.properties` |
| Thanh toán OK nhưng DB vẫn `PENDING` | Webhook chưa tới server (ngrok tắt / chưa đăng ký webhook) |

---

## 5. Đăng ký webhook PayOS

Sau khi có ngrok URL, đăng ký webhook **một lần** (hoặc mỗi khi đổi ngrok URL):

**Cách 1 — qua Swagger / Postman:**

```http
POST https://<ngrok-url>/api/payments/payos/confirm-webhook
Content-Type: application/json

{
  "webhookUrl": "https://<ngrok-url>/api/payments/payos/webhook"
}
```

**Cách 2 — trên dashboard [my.payos.vn](https://my.payos.vn)**

Đặt Webhook URL = `https://<ngrok-url>/api/payments/payos/webhook`

---

## 6. Chạy ứng dụng

```bash
.\gradlew.bat bootRun
```

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

Tài khoản test (xem `docs/db/insert_test_users.sql`):

| Role | Email | Password |
|---|---|---|
| Staff | `staff1@example.com` | `123` |
| Driver | `driver1@example.com` | `1` |

Đăng nhập lấy JWT: `POST /api/auth/login` → Authorize Bearer token trên Swagger.

---

## 7. Luồng test PayOS (Swagger)

### Điều kiện trước

- Xe đã **check-in** → `parking_sessions.session_status = ACTIVE`
- `payment_status = UNPAID`
- Có `sessionId`, `driverId`, `ticketCode`

### Bước 1 — Staff tạo link thanh toán

```http
POST /api/payments/initiate
```

```json
{
  "sessionId": "<session-id>",
  "paymentMethod": "PAYOS",
  "amount": 5000,
  "driverId": "<driver-user-id>",
  "note": "Test PayOS"
}
```

Response có `paymentUrl` → mở link trên trình duyệt → thanh toán.

### Bước 2 — PayOS callback (tự động)

| Endpoint | Ai gọi | Tác dụng |
|---|---|---|
| `POST /api/payments/payos/webhook` | PayOS server | Cập nhật DB: `payment SUCCESS`, `session PAID` |
| `GET /api/payments/payos/return` | Trình duyệt driver | Chỉ hiển thị kết quả, **không** cập nhật DB |
| `GET /api/payments/payos/cancel` | Trình duyệt driver | User hủy thanh toán |

Kiểm tra sau webhook:

```http
GET /api/payments/{paymentId}
```

### Bước 3 — Staff xác nhận cho tài xế

```http
POST /api/payments/confirm-by-staff
```

```json
{
  "paymentId": "<payment-id>",
  "sessionId": "<session-id>",
  "driverId": "<driver-id>",
  "staffId": "<staff-user-id>",
  "isConfirmed": true
}
```

> Chỉ gửi các field trên. **Không** gửi `confirmedAt`, `confirmationStatus`, `message` — server tự set.

Response: `confirmationStatus: "SUCCESS"`

### Bước 4 — Checkout (xe ra, trả slot)

```http
POST /api/sessions/checkout
```

```json
{
  "ticketCode": "<ticket-code>",
  "paymentMethod": "PAYOS"
}
```

---

## 8. Luồng CASH (đơn giản)

Không cần PayOS / ngrok. Staff gọi thẳng:

```http
POST /api/sessions/checkout
```

```json
{
  "ticketCode": "<ticket-code>",
  "paymentMethod": "CASH"
}
```

Một API xử lý: tạo payment + `PAID` + `COMPLETED` + giải phóng slot.

---

## 9. Các API payment chính

| API | Mô tả |
|---|---|
| `POST /api/payments/initiate` | Tạo payment PENDING + link PayOS/VNPay |
| `POST /api/payments/confirm-success` | Confirm thủ công (dev/test) |
| `POST /api/payments/confirm-by-staff` | Staff duyệt cho tài xế ra |
| `POST /api/payments/handle-failure` | Đánh dấu thất bại |
| `GET /api/payments/{paymentId}` | Xem chi tiết payment |
| `POST /api/payments/payos/webhook` | Callback PayOS (public, không JWT) |
| `GET /api/payments/payos/return` | Redirect sau thanh toán |
| `POST /api/payments/payos/confirm-webhook` | Đăng ký webhook URL |

Callback PayOS/VNPay được mở public trong `SecurityConfig` (không cần JWT).

---

## 10. Lỗi thường gặp khi test

| Lỗi | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Description is invalid - too long` | PayOS giới hạn `description` ~25 ký tự | Đã fix trong `PayOSService` (`"Phi do xe"`) |
| `Data truncated for column 'payment_method'` | DB thiếu `PAYOS` trong ENUM | Chạy `docs/db/alter_payments_add_payos.sql` |
| `ERR_NGROK_3200` | ngrok offline | Bật lại ngrok, cập nhật URL, restart app |
| `Session is not active` | Session đã checkout hoặc chưa check-in | Check-in lại |
| `Payment has not been confirmed yet` | Webhook chưa chạy | Kiểm tra ngrok + webhook |
| `Cannot deserialize LocalDateTime` | Gửi `confirmedAt` sai format trong request | Bỏ `confirmedAt` khỏi body `confirm-by-staff` |
| `401 Unauthorized` | Chưa Authorize JWT trên Swagger | Login → Bearer token |

---

## 11. Thứ tự chạy đúng (checklist)

```
[ ] MySQL chạy, đã migrate payments ENUM
[ ] application.properties có PayOS credentials + ngrok URL
[ ] .\gradlew.bat bootRun
[ ] ngrok http 8080  (giữ chạy)
[ ] POST /api/payments/payos/confirm-webhook
[ ] Login Swagger → Authorize token
[ ] Có session ACTIVE (đã check-in)
[ ] POST /api/payments/initiate
[ ] Mở paymentUrl → thanh toán
[ ] GET /api/payments/{id} → SUCCESS / PAID
[ ] POST /api/payments/confirm-by-staff
[ ] POST /api/sessions/checkout
```

---

## 12. File liên quan trong project

| File | Nội dung |
|---|---|
| `src/main/resources/application.properties` | Credentials + callback URL |
| `src/main/java/.../service/PayOSService.java` | Tạo payment link PayOS |
| `src/main/java/.../service/PaymentService.java` | Luồng initiate / confirm |
| `src/main/java/.../controller/PayOSController.java` | Webhook, return, cancel |
| `src/main/java/.../config/PayOSConfig.java` | Bean PayOS client |
| `src/main/java/.../config/SecurityConfig.java` | Public callback routes |
| `docs/db/alter_payments_add_payos.sql` | Migration ENUM payments |
