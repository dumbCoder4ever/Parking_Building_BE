# Swagger / Try-it examples — Parking session flows

Tập hợp ví dụ dùng để Paste vào Swagger UI / Postman.

Base URL: `http://localhost:8080`

---

## 1) Đăng ký (Driver)
POST /api/auth/register

Body:

{
  "username": "driver-test",
  "password": "123456",
  "fullName": "Driver Test",
  "phoneNumber": "0909000000",
  "email": "driver.test@gmail.com"
}

Expected success: HTTP 200, body.data chứa profile.

---

## 2) Login (Driver/Staff)
POST /api/auth/login

Body:

{
  "gmail": "driver.test@gmail.com",
  "password": "123456"
}

Expected success: HTTP 200, `data.token` là JWT. Dùng header `Authorization: Bearer <token>` cho các request cần auth.

---

## 3) Tạo reservation (Driver)
POST /api/reservations
Headers: `Authorization: Bearer <token>`

Body (ví dụ):

{
  "plateNumber": "51A-12345",
  "vehicleColor": "White",
  "brand": "Honda",
  "model": "City",
  "vehicleTypeId": "<vehicleTypeId>",
  "reservationStart": "2026-06-03T09:00:00",
  "reservationEnd": "2026-06-03T12:00:00"
}

Expected success: HTTP 200, `data.ticketCode` và `data.qrCode`.

---

## 4) Staff check-in
POST /api/sessions/checkin
Headers: `Authorization: Bearer <staff-token>`

Body:

{
  "ticketCode": "<ticketCode>",
  "qrCode": "<qrCode-if-any>",
  "plateNumber": "51A-12345",
  "vehicleColor": "White",
  "vehicleTypeId": "<vehicleTypeId>"
}

Success response (ví dụ):

{
  "data": {
    "sessionId": "<uuid>",
    "ticketCode": "T-xxx",
    "slotId": "<slotId>",
    "slotName": "A1-01",
    "vehiclePlate": "51A-12345",
    "checkinTime": "2026-06-03T09:05:00"
  }
}

Errors:
- Ticket not found
- Ticket already used
- Reservation expired
- Slot is not in RESERVED status

---

## 5) Staff checkout / tính phí
POST /api/sessions/checkout
Headers: `Authorization: Bearer <staff-token>`

Body:

{
  "ticketCode": "<ticketCode>",
  "paymentMethod": "CASH"
}

Success response (ví dụ):

{
  "data": {
    "sessionId": "<uuid>",
    "checkoutTime": "2026-06-03T11:50:00",
    "totalFee": 25000.00,
    "paymentId": "<payment-uuid>"
  }
}

Notes on fee calculation (the code):
- Duration (minutes) → hours = ceil(minutes / 60)
- total = basePrice + hourlyRate * hours
- apply peakHourMultiplier (morning 07-09, evening 17-19) if configured
- cap by maxDailyFee if configured

Errors:
- Active parking session not found for this ticket
- Pricing policy not found for vehicle type

---

## Importing Postman collection
File: docs/postman/parking-session-flows.postman_collection.json
1. Open Postman → Import → chọn file.
2. Mở collection, chỉnh `baseUrl` collection variable nếu cần, chạy `Login` để set `token`.
3. Chạy các request theo thứ tự: Register → Login → Create Reservation → Check-in → Checkout.

---

Nếu bạn muốn, mình có thể: commit + push các file này lên branch `feat/session-flow` (y/n), hoặc sinh thêm file Postman Collection cho environments.