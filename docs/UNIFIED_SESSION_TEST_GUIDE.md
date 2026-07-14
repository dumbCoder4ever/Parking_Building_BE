# Hướng dẫn test luồng DRIVER / GUEST với unified checkin-checkout

Base URL: `http://localhost:8080`

**Lưu ý chung:**
- Tất cả request cần header `Authorization: Bearer <token>`.
- `multipart/form-data` dùng khi upload file ảnh.
- Endpoint chính hiện tại:
  - `POST /api/sessions/checkin`
  - `POST /api/sessions/checkout`

---

## 1) Chuẩn bị tài khoản test

| Role | Email | Password |
|------|-------|----------|
| Driver | driver1@example.com | 1 |
| Staff | staff1@example.com | 123 |
| Manager | manager1@example.com | 123 |
| Admin | admin@example.com | 123 |

### Login lấy token

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"staff1@example.com","password":"123"}'
```

Lưu `data.token` dùng cho các request sau.

---

## 2) Flow DRIVER

### 2.1 Driver đăng ký reservation

```bash
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer <driver-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "plateNumber": "51A-12345",
    "vehicleColor": "White",
    "vehicleBrand": "Honda",
    "vehicleModel": "Future",
    "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
    "buildingId": "building-id"
  }'
```

→ Copy `ticketCode` để staff checkin/checkout.

### 2.2 Staff check-in DRIVER (manual)

```bash
curl -X POST "http://localhost:8080/api/sessions/checkin" \
  -H "Authorization: Bearer <staff-token>" \
  -F "ticketCode=TKT-1234567890-456" \
  -F "plateNumber=51A-12345" \
  -F "vehicleColor=White" \
  -F "vehicleTypeId=33333333-3333-3333-3333-333333333331" \
  -F "checkinImage=@C:\path\to\checkin.jpg"
```

### 2.3 Staff check-in DRIVER (OCR)

```bash
curl -X POST "http://localhost:8080/api/sessions/checkin" \
  -H "Authorization: Bearer <staff-token>" \
  -F "buildingId=building-id" \
  -F "vehicleTypeId=33333333-3333-3333-3333-333333333331" \
  -F "plateImage=@C:\path\to\plate.jpg"
```

→ Hệ thống tự OCR → tìm reservation → tạo session → trả về `ticketCode`.

### 2.4 Xem phí ước tính

```bash
curl "http://localhost:8080/api/sessions/estimate?ticketCode=TKT-1234567890-456" \
  -H "Authorization: Bearer <staff-token>"
```

### 2.5 Staff checkout DRIVER (CASH)

```bash
curl -X POST "http://localhost:8080/api/sessions/checkout" \
  -H "Authorization: Bearer <staff-token>" \
  -F "ticketCode=TKT-1234567890-456" \
  -F "paymentMethod=CASH" \
  -F "checkoutImage=@C:\path\to\checkout.jpg"
```

### 2.6 Staff checkout DRIVER (electronic)

Sau khi driver thanh toán VNPay/PayOS/MOMO:

```bash
curl -X PATCH "http://localhost:8080/api/sessions/{sessionId}/confirm-exit?paymentMethod=VNPAY" \
  -H "Authorization: Bearer <staff-token>" \
  -F "checkoutImage=@C:\path\to\checkout.jpg"
```

---

## 3) Flow GUEST

### 3.1 Staff check-in GUEST (OCR)

```bash
curl -X POST "http://localhost:8080/api/sessions/checkin" \
  -H "Authorization: Bearer <staff-token>" \
  -F "buildingId=building-id" \
  -F "vehicleTypeId=33333333-3333-3333-3333-333333333331" \
  -F "vehicleColor=White" \
  -F "guestName=Nguyen Van A" \
  -F "guestPhone=0909123456" \
  -F "note=Guest parking" \
  -F "plateImage=@C:\path\to\plate.jpg"
```

→ Hệ thống tự OCR → kiểm tra biển số chưa có session ACTIVE → auto-assign slot → tạo session + ticket `G-xxx`.

### 3.2 Staff checkout GUEST (ticketCode)

```bash
curl -X POST "http://localhost:8080/api/sessions/checkout" \
  -H "Authorization: Bearer <staff-token>" \
  -F "ticketCode=G-1751659789123-4827" \
  -F "paymentMethod=CASH" \
  -F "checkoutImage=@C:\path\to\checkout.jpg"
```

### 3.3 Staff checkout GUEST (OCR + ticketCode)

```bash
curl -X POST "http://localhost:8080/api/sessions/checkout" \
  -H "Authorization: Bearer <staff-token>" \
  -F "ticketCode=G-1751659789123-4827" \
  -F "plateImage=@C:\path\to\plate_out.jpg" \
  -F "paymentMethod=CASH" \
  -F "checkoutImage=@C:\path\to\checkout.jpg"
```

→ Hệ thống OCR validate biển số khớp session rồi mới checkout.

---

## 4) Tra cứu guest session

```bash
curl "http://localhost:8080/api/sessions/guest/plate/51H-99999" \
  -H "Authorization: Bearer <staff-token>"

curl "http://localhost:8080/api/sessions/guest/ticket/G-1751659789123-4827" \
  -H "Authorization: Bearer <staff-token>"

curl "http://localhost:8080/api/sessions/guest/{sessionId}" \
  -H "Authorization: Bearer <staff-token>"
```

---

## 5) Kiểm tra kết quả mong muốn

### Check-in thành công

- DRIVER: session status `CHECKED_IN`, slot `OCCUPIED`, reservation `PAID/APPROVED`.
- GUEST: session status `ACTIVE`, slot `OCCUPIED`, reservation `null`, ticket `G-xxx`.

### Checkout thành công

- `totalFee` > 0
- `sessionStatus`: `CHECKED_OUT` hoặc `COMPLETED`
- `paymentStatus`: `PAID`
- slot trở về `AVAILABLE`

### Lỗi thường gặp

| Code | Nguyên nhân | Cách xử lý |
|------|-------------|------------|
| `TICKET_NOT_FOUND` | ticketCode sai | kiểm tra lại mã vé |
| `PLATE_MISMATCH` | biển số quét không khớp | kiểm tra xe thực tế |
| `PLATE_ALREADY_PARKED` | biển số đang có session ACTIVE | checkout xe đó trước |
| `SLOT_NOT_AVAILABLE` | hết slot phù hợp | chuyển building khác |
| `OCR_FAILED` | ảnh mờ | chụp lại rõ hơn |
| `RESERVATION_NOT_FOUND` | driver không có reservation | chuyển guest mode |
| `PAYMENT_NOT_COMPLETED` | chưa thanh toán electronic | hoàn tất payment trước |

---

## 6) Checklist test nhanh

- [ ] Staff checkin driver bằng ticketCode + plateNumber
- [ ] Staff checkin driver bằng OCR plateImage + buildingId
- [ ] Staff checkout driver CASH
- [ ] Staff checkout driver VNPAY/PAYOS/MOMO qua confirm-exit
- [ ] Staff checkin guest bằng OCR plateImage + buildingId
- [ ] Staff checkout guest bằng ticketCode
- [ ] Staff checkout guest bằng OCR + ticketCode
- [ ] Tra cứu guest session qua plate/ticket/sessionId
