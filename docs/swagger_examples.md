# Swagger / Try-it examples — Parking Building BE

Base URL: `http://localhost:8080`

---

# LUỒNG MỚI (CÓ RESERVATION ĐẶT TRƯỚC)

## FLOW DRIVER - ĐẶT CHỖ TRƯỚC

### Bước 1: Login
Driver đăng nhập vào hệ thống.

### Bước 2: Xem thông tin bãi xe
```
GET /api/buildings/{buildingId}
```

### Bước 3: Xem slot còn trống
```
GET /api/reservations/availability?buildingId=xxx&vehicleTypeId=yyy
```
Trả về: Building → Floor → Vehicle Type → Zone → Slots

### Bước 4: Đăng ký đặt chỗ (Tạo reservation)
```
POST /api/reservations
```
Hệ thống:
- Tạo reservation_code và ticket_code cho driver
- Đổi trạng thái slot: AVAILABLE → RESERVED
- Reservation status: `PENDING_PAYMENT`

### Bước 5: Driver thanh toán (VNPay/PayOS/MOMO)
```
POST /api/payments/initiate
```
Sau khi thanh toán thành công:
- Reservation status: `PAID`

### Bước 6: Driver đến bãi xe
Driver đưa:
- ticket_code
- biển số xe

### Bước 7: Staff check-in xe
Staff kiểm tra và xác nhận:
```
POST /api/sessions/checkin
Body: {
  "ticketCode": "TKT-xxx",
  "plateNumber": "51A-12345",
  "checkinPlateImage": "base64-image-string",
  "checkinVehicleImage": "base64-image-string"
}
```
Hệ thống:
- Tạo Parking Session
- Cập nhật slot: RESERVED → OCCUPIED
- Session status: `CHECKED_IN`
- Ghi nhận: checkin_time, slot, vehicle, staff xử lý, hình ảnh

### Bước 8: Staff xác nhận xe ra

**A. Thanh toán CASH:**
```
POST /api/sessions/checkout
Body: {
  "ticketCode": "TKT-xxx",
  "paymentMethod": "CASH",
  "checkoutPlateImage": "base64-image-string",
  "checkoutVehicleImage": "base64-image-string"
}
```
Hệ thống:
- Session status: `CHECKED_OUT`
- Reservation status: `COMPLETED`
- Tính phí theo thời gian gửi
- Cập nhật slot: OCCUPIED → AVAILABLE

**B. Thanh toán điện tử (VNPay/PayOS/MOMO):**
Staff gọi confirm-exit sau khi payment webhook confirmed:
```
PATCH /api/sessions/{sessionId}/confirm-exit?paymentMethod=VNPAY
```
Hệ thống:
- Session status: `COMPLETED`
- Reservation status: `COMPLETED`
- Cập nhật slot: OCCUPIED → AVAILABLE

---

## FLOW STAFF - XỬ LÝ XE VÀO/RA

### Check-in xe (có OCR):
```
1. POST /api/ocr/plate/upload  (file ảnh biển số)
   → Lấy plateNumber + duplicateActiveSession
2. Nếu duplicateActiveSession != null → xử lý cảnh báo (xem mục OCR)
3. POST /api/sessions/checkin
Headers: Authorization: Bearer <staff-token>
Body: {
  "ticketCode": "TKT-xxx",
  "plateNumber": "51A-12345",
  "checkinPlateImage": "base64-image-string",
  "checkinVehicleImage": "base64-image-string"
}
```

### Check-in xe (guest / vãng lai - có OCR):
```
1. POST /api/ocr/plate/upload  (file ảnh biển số)
   → Lấy plateNumber + duplicateActiveSession
2. Nếu duplicateActiveSession != null → popup cảnh báo (staff xác nhận mới tiếp tục)
3. POST /api/sessions/guest/checkin
Headers: Authorization: Bearer <staff-token>
Body: {
  "plateNumber": "51A-12345",
  "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
  "slotId": "slot-id",
  "vehicleColor": "White"
}
```

### Check-out xe (CASH - có OCR):
```
1. POST /api/ocr/plate/upload  (file ảnh biển số)
   → Lấy plateNumber + duplicateActiveSession
2. Nếu duplicateActiveSession != null VÀ khác session đang checkout → cảnh báo
3. POST /api/sessions/checkout
Headers: Authorization: Bearer <staff-token>
Body: {
  "ticketCode": "TKT-xxx",
  "paymentMethod": "CASH",
  "checkoutPlateImage": "base64-image-string",
  "checkoutVehicleImage": "base64-image-string"
}
```

### Xem session đang hoạt động:
```
GET /api/staff/sessions/active?buildingId=xxx
```

### Dự đoán phí trước khi checkout:
```
GET /api/sessions/estimate?ticketCode=TKT-xxx
Headers: Authorization: Bearer <staff-token>
```

---

## FLOW MỚI - Thanh toán điện tử (VNPay / PayOS / MOMO)

Flow này dành cho trường hợp driver chọn thanh toán điện tử. Có thêm bước driver xác nhận đã thanh toán trước khi staff cho xe ra.

### Bước 1: Staff check-in xe (như bình thường)
```
POST /api/sessions/checkin
Headers: Authorization: Bearer <staff-token>
Body: {
  "ticketCode": "TKT-xxx",
  "plateNumber": "51A-12345",
  "checkinPlateImage": "base64-image-string",
  "checkinVehicleImage": "base64-image-string"
}
```

### Bước 2: Dự đoán phí (Driver xem phí trước khi thanh toán)
```
GET /api/sessions/estimate?ticketCode=TKT-xxx
Headers: Authorization: Bearer <staff-token> hoặc <driver-token>
```
Trả về chi tiết phí: totalFee, basePrice, hourlyRate, thời gian gửi.

### Bước 3: Staff tạo payment link (VNPay/PayOS/MOMO)
```
POST /api/payments/initiate
Headers: Authorization: Bearer <staff-token>
Body: {
  "sessionId": "session-id",
  "paymentMethod": "VNPAY",   // hoặc "PAYOS", "MOMO"
  "amount": 20000,
  "driverId": "driver-user-id"
}
```
Trả về: `paymentId`, `paymentUrl` (link thanh toán cho driver).

### Bước 4: Driver nhận notification
- Driver nhận được link thanh toán qua notification
- Driver mở link → thanh toán trên gateway (VNPay/PayOS/MOMO)

### Bước 5: Gateway webhook xác nhận (server-to-server)
- VNPay → `GET /api/payments/vnpay/ipn`
- PayOS → `POST /api/payments/payos/webhook`
- MOMO → Tương tự webhook
- Hệ thống tự động cập nhật payment = SUCCESS, session.paymentStatus = PAID

### Bước 6: Staff xác nhận xe ra (Confirm Exit)
```
PATCH /api/sessions/{sessionId}/confirm-exit?paymentMethod=VNPAY
Headers: Authorization: Bearer <staff-token>
```
- Staff gọi API này sau khi biết payment đã thành công
- Slot chuyển sang AVAILABLE, reservation = COMPLETED, session = COMPLETED

### Tóm tắt trạng thái Session mới

| Trạng thái Session | Ý nghĩa |
|---|---|
| CHECKED_IN | Xe đã vào bãi, đang đỗ |
| CHECKED_OUT | Xe đã checkout (trả tiền mặt) |
| COMPLETED | Checkout hoàn tất |
| OVERDUE | Quá giờ đặt chỗ |
| CANCELLED | Session bị hủy |

### Tóm tắt trạng thái Reservation mới

| Trạng thái Reservation | Ý nghĩa |
|---|---|
| PENDING_PAYMENT | Chờ thanh toán |
| PAID | Đã thanh toán |
| COMPLETED | Hoàn tất (đã ra) |
| CANCELLED | Bị hủy |
| EXPIRED | Hết hạn |

---

# API REFERENCE

## 1) Chuẩn bị dữ liệu trước khi test

### 1.1 Tài khoản test

| Role | Email | Password |
|------|-------|----------|
| Driver | driver1@example.com | 1 |
| Staff | staff1@example.com | 123 |
| Manager | manager1@example.com | 123 |
| Admin | admin@example.com | 123 |

---

## 2) Authentication

### Login
POST `/api/auth/login`

```json
{
  "email": "driver1@example.com",
  "password": "1"
}
```

Response:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

Dùng header: `Authorization: Bearer <token>`

---

## 3) DRIVER APIs

### 3.1 Đăng ký gửi xe (Tạo lượt gửi)
POST `/api/reservations`

**Headers:** `Authorization: Bearer <driver-token>`

**Body:**
```json
{
  "plateNumber": "51A-12345",
  "vehicleColor": "White",
  "vehicleBrand": "Honda",
  "vehicleModel": "Future",
  "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
  "buildingId": "building-id"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Reservation created successfully. Please show your ticket when checking in.",
  "data": {
    "registrationId": "reg123",
    "ticketCode": "TKT-1234567890-456",
    "reservationStatus": "PENDING",
    "plateNumber": "51A-12345",
    "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
    "vehicleTypeName": "Motorbike",
    "buildingId": "building-id",
    "buildingName": "Main Parking Building",
    "message": "Registration successful. Show this ticket code to staff at entrance."
  }
}
```

---

### 3.2 Xem lượt gửi xe của tôi
GET `/api/reservations/me`

**Headers:** `Authorization: Bearer <driver-token>`

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "registrationId": "reg123",
      "ticketCode": "TKT-1234567890-456",
      "reservationStatus": "COMPLETED",
      "plateNumber": "51A-12345",
      "buildingId": "building-id",
      "buildingName": "Main Parking Building",
      "checkinTime": "2026-06-10T14:05:00",
      "checkoutTime": "2026-06-10T17:30:00"
    }
  ]
}
```

---

### 3.2.1 Khảo sát availability (Flow mới — 2 endpoint dùng cho màn tìm chỗ)

> 2 endpoint FE cần call để hiển thị màn availability. Đã thay thế các endpoint cũ `/api/slots/availability` và `/api/buildings/{id}` để giảm payload và round-trip.

**Bước 1 — Lấy danh sách bãi xe đang có chỗ trống**

```
GET /api/buildings/available
```

**Headers:** `Authorization: Bearer <driver-token>`

**Query params (tất cả optional):**
- `vehicleTypeId` — lọc theo loại xe (vd `CAR`, `MOTORBIKE`)
- `name` — partial match tên bãi
- `address` — partial match địa chỉ

**Response (200):**
```json
{
  "success": true,
  "message": "Available buildings retrieved successfully",
  "data": [
    {
      "buildingId": "BLD-001",
      "name": "Bãi xe Trung Tâm",
      "address": "12 Lê Lợi, Q1",
      "contactNumber": "0901234567",
      "operatingStartTime": "06:00",
      "operatingEndTime": "23:00",
      "operatingHoursDisplay": "06:00 - 23:00",
      "parkingRules": "Vui lòng đặt trước chỗ đỗ xe. Xuất trình mã vé khi check-in. Giữ vé cẩn thận khi rời khỏi bãi đỗ.",
      "totalSlots": 50,
      "availableSlots": 12,
      "vehicleTypes": ["Car", "Motorbike"],
      "pricingByType": [
        {
          "policyId": "PP-001",
          "vehicleTypeId": "VT-CAR",
          "vehicleTypeName": "Car",
          "pricingType": "HOURLY",
          "basePrice": 5000,
          "hourlyRate": 5000,
          "maxHours": 24
        }
      ]
    }
  ]
}
```

> FE render card/list bãi xe với `totalSlots` / `availableSlots` / `vehicleTypes`. Mỗi card không cần gọi thêm API nào khác để hiện pricing.

---

**Bước 2 — Lấy grid slot của một zone (khi user chọn bãi + zone)**

> Sau khi biết bãi xe có chỗ trống, FE cần 1 zone id để gọi endpoint này. Zone id có thể lấy từ nguồn khác (vd cache từ màn manager setup, hoặc từ 1 lookup nhẹ khác — chưa có API list zone public ở flow này).

```
GET /api/zones/{zoneId}/slots
```

**Headers:** `Authorization: Bearer <driver-token>`

**Path params:**
- `zoneId` — id của zone

**Response (200):**
```json
{
  "success": true,
  "message": "Zone slots retrieved successfully",
  "data": {
    "buildingId": "BLD-001",
    "buildingName": "Bãi xe Trung Tâm",
    "zoneId": "Z-001",
    "zoneName": "Zone A",
    "zoneStatus": "ACTIVE",
    "floorId": "FL-001",
    "floorName": "Tầng 1",
    "floorLevel": 1,
    "floorVehicleTypeId": "VT-CAR",
    "floorVehicleTypeName": "Car",
    "totalSlots": 20,
    "availableSlots": 15,
    "slots": [
      {
        "slotId": "SL-001",
        "slotName": "A-001",
        "slotStatus": "AVAILABLE",
        "vehicleTypeId": "VT-CAR",
        "vehicleTypeName": "Car",
        "availableCount": 1,
        "basePrice": 5000,
        "hourlyRate": 5000,
        "maxHours": 24,
        "reservedByUserId": null,
        "reservedByUsername": null,
        "reservedByVehicleId": null
      },
      {
        "slotId": "SL-002",
        "slotName": "A-002",
        "slotStatus": "OCCUPIED",
        "vehicleTypeId": "VT-CAR",
        "vehicleTypeName": "Car",
        "availableCount": 0,
        "basePrice": 5000,
        "hourlyRate": 5000,
        "maxHours": 24,
        "reservedByUserId": "USR-99",
        "reservedByUsername": "driver01",
        "reservedByVehicleId": "VEH-12"
      }
    ]
  }
}
```

> FE dùng `slots[]` render lưới slot. Slot `AVAILABLE` cho phép click chọn; `OCCUPIED` không cho chọn. Field `reservedByUsername` chỉ hiện cho STAFF/MANAGER/ADMIN (driver khác không nên thấy — backend sẽ mask ở bản sau).

---

### 3.3 Xem slot còn trống
GET `/api/slots/availability`

**Headers:** `Authorization: Bearer <driver-token>`

**Query params (tùy chọn):**
- `buildingId` - lọc theo tòa nhà
- `vehicleTypeId` - lọc theo loại xe

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "buildingId": "abc123",
      "buildingName": "Main Parking Building",
      "floorId": "floor1",
      "floorName": "Floor 1 - Motorbike",
      "floorLevel": 1,
      "floorVehicleTypeId": "33333333-3333-3333-3333-333333333331",
      "floorVehicleTypeName": "Motorbike",
      "zoneId": "zone1",
      "zoneName": "Zone A",
      "zoneStatus": "ACTIVE",
      "totalSlots": 20,
      "availableSlots": 15,
      "slots": [
        {
          "slotId": "slot1",
          "slotName": "A-001",
          "slotStatus": "AVAILABLE"
        }
      ]
    }
  ]
}
```

---

### 3.4 Xem thông tin bãi xe
GET `/api/buildings/{buildingId}`

**Headers:** `Authorization: Bearer <driver-token>`

**Response:**
```json
{
  "success": true,
  "data": {
    "buildingId": "abc123",
    "buildingName": "Main Parking Building",
    "address": "123 Main Street",
    "openingTime": "06:00",
    "closingTime": "23:00",
    "status": "ACTIVE",
    "totalSlots": 100,
    "availableSlots": 75,
    "pricingSummary": "Please check pricing at the building"
  }
}
```

---

### 3.5 Xem các loại xe
GET `/api/vehicles/types`

**Headers:** `Authorization: Bearer <driver-token>`

---

### 3.6 Xem lịch sử parking sessions
GET `/api/users/me/sessions`

**Headers:** `Authorization: Bearer <driver-token>`

**Query params:**
- `limit` (default: 20)

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "sessionId": "session123",
      "ticketCode": "TKT-xxx",
      "plateNumber": "51A-12345",
      "vehicleTypeName": "Motorbike",
      "buildingName": "Main Parking Building",
      "floorName": "Floor 1",
      "zoneName": "Zone A",
      "slotName": "A-001",
      "checkinTime": "2026-06-10T14:05:00",
      "checkoutTime": "2026-06-10T17:30:00",
      "parkingMinutes": 205,
      "parkingHours": 4,
      "currentFee": 12000,
      "sessionStatus": "COMPLETED",
      "paymentStatus": "PAID"
    }
  ]
}
```

---

## 4) STAFF APIs

### 4.1 Check-in xe
POST `/api/sessions/checkin`

**Headers:** `Authorization: Bearer <staff-token>`

**Body:**
```json
{
  "ticketCode": "TKT-xxx",
  "plateNumber": "51A-12345",
  "vehicleColor": "White",
  "checkinPlateImage": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "checkinVehicleImage": "data:image/jpeg;base64,/9j/4AAQSkZJRg..."
}
```

**Response (thành công):**
```json
{
  "success": true,
  "message": "Check-in successful",
  "data": {
    "sessionId": "abc123-def456",
    "ticketCode": "TKT-xxx",
    "vehiclePlate": "51A-12345",
    "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
    "vehicleTypeName": "Motorbike",
    "buildingId": "building-id",
    "buildingName": "Main Parking Building",
    "floorId": "floor-1",
    "floorName": "Floor 1 - Motorbike",
    "zoneId": "zone-a",
    "zoneName": "Zone A",
    "slotId": "slot-001",
    "slotName": "A-001",
    "checkinTime": "2026-06-21T14:05:00",
    "sessionStatus": "CHECKED_IN",
    "paymentStatus": "UNPAID",
    "estimatedFee": 5000,
    "basePrice": 5000,
    "hourlyRate": 3000
  }
}
```

**Lưu ý:**
- `checkinPlateImage` và `checkinVehicleImage` là bắt buộc (base64 string)
- Sau check-in thành công, slot chuyển từ `RESERVED` → `OCCUPIED`
- Session status mới: `CHECKED_IN`

---

### 4.2 Check-out xe (Thu phí - CASH)
POST `/api/sessions/checkout`

**Headers:** `Authorization: Bearer <staff-token>`

**Body:**
```json
{
  "ticketCode": "TKT-xxx",
  "paymentMethod": "CASH",
  "checkoutPlateImage": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "checkoutVehicleImage": "data:image/jpeg;base64,/9j/4AAQSkZJRg..."
}
```

**Response (thành công):**
```json
{
  "success": true,
  "message": "Checkout successful",
  "data": {
    "sessionId": "abc123-def456",
    "ticketCode": "TKT-xxx",
    "vehiclePlate": "51A-12345",
    "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
    "vehicleTypeName": "Motorbike",
    "buildingId": "building-id",
    "buildingName": "Main Parking Building",
    "floorId": "floor-1",
    "floorName": "Floor 1 - Motorbike",
    "zoneId": "zone-a",
    "zoneName": "Zone A",
    "slotId": "slot-001",
    "slotName": "A-001",
    "checkinTime": "2026-06-21T14:05:00",
    "checkoutTime": "2026-06-21T17:30:00",
    "parkingHours": 4,
    "parkingMinutes": 205,
    "sessionStatus": "CHECKED_OUT",
    "paymentStatus": "PAID",
    "totalFee": 14000,
    "paymentId": "payment-uuid",
    "paymentMethod": "CASH",
    "basePrice": 5000,
    "hourlyRate": 3000
  }
}
```

**Lưu ý:**
- `checkoutPlateImage` và `checkoutVehicleImage` là bắt buộc (base64 string)
- Sau checkout thành công: slot chuyển `OCCUPIED` → `AVAILABLE`
- Session status: `CHECKED_OUT`, Reservation status: `COMPLETED`
- Với thanh toán điện tử (VNPay/PayOS/MOMO), dùng `PATCH /api/sessions/{sessionId}/confirm-exit`

---

### 4.3 Xem các buildings đã được assign
GET `/api/staff/buildings`

**Headers:** `Authorization: Bearer <staff-token>`

---

### 4.4 Xem tất cả reservations
GET `/api/staff/reservations`

**Headers:** `Authorization: Bearer <staff-token>`

**Query params:**
- `buildingId` — **BẮT BUỘC** với staff, để lọc reservation theo building được assign

---

### 4.5 Xem reservations theo Status
GET `/api/staff/reservations/by-status`

**Headers:** `Authorization: Bearer <staff-token>`

**Query params:**
- `buildingId` — **BẮT BUỘC** với staff
- `status` (PENDING, APPROVED, COMPLETED, etc.)

---

### 4.6 Xem reservation theo Ticket Code
GET `/api/staff/reservations/code/{ticketCode}`

**Headers:** `Authorization: Bearer <staff-token>`

**Query params:**
- `buildingId` — **BẮT BUỘC** với staff

---

### 4.7 Cập nhật trạng thái reservation
PATCH `/api/staff/reservations/{ticketCode}/status`

**Headers:** `Authorization: Bearer <staff-token|admin-token>`

**Query params:**
- `buildingId` — **BẮT BUỘC** với staff

**Body:**
```json
{
  "status": "APPROVED",
  "note": "Approved"
}
```

**Status values:** PENDING, APPROVED, REJECTED, CANCELLED, COMPLETED

---

### 4.8 Xem sessions đang hoạt động (CHECKED_IN)
GET `/api/staff/sessions/active`

**Headers:** `Authorization: Bearer <staff-token>`

**Query params:**
- `buildingId` — để lọc theo building

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "sessionId": "abc123-def456",
      "ticketCode": "TKT-xxx",
      "vehiclePlate": "51A-12345",
      "slotName": "A-001",
      "checkinTime": "2026-06-21T14:05:00",
      "sessionStatus": "CHECKED_IN",
      "paymentStatus": "UNPAID"
    }
  ]
}
```

---

### 4.9 Xem sessions theo Status
GET `/api/staff/sessions`

**Headers:** `Authorization: Bearer <staff-token>`

**Query params:**
- `buildingId` — để lọc theo building
- `status` — CHECKED_IN, CHECKED_OUT, COMPLETED, OVERDUE

---

## 5) SESSION & PAYMENT APIs

### 5.1 Dự đoán phí (Estimate Fee)
```
GET /api/sessions/estimate?ticketCode=TKT-xxx
Headers: Authorization: Bearer <staff-token> hoặc <driver-token>
```

**Response:**
```json
{
  "success": true,
  "message": "Fee estimated successfully",
  "data": {
    "sessionId": "abc123-def456",
    "ticketCode": "TKT-xxx",
    "vehiclePlate": "51A-12345",
    "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
    "vehicleTypeName": "Motorbike",
    "checkinTime": "2026-06-21T14:05:00",
    "estimatedCheckoutTime": "2026-06-21T18:05:00",
    "parkingHours": 4,
    "parkingMinutes": 15,
    "totalFee": 14000,
    "basePrice": 5000,
    "hourlyRate": 3000,
    "buildingId": "building-id",
    "buildingName": "Main Parking Building",
    "floorId": "floor-1",
    "floorName": "Floor 1 - Motorbike",
    "zoneId": "zone-a",
    "zoneName": "Zone A",
    "slotId": "slot-001",
    "slotName": "A-001"
  }
}
```

---

### 5.2 Tạo Payment (VNPay / PayOS / MOMO)
```
POST /api/payments/initiate
Headers: Authorization: Bearer <staff-token>
```

**Body:**
```json
{
  "sessionId": "abc123-def456",
  "paymentMethod": "VNPAY",
  "amount": 14000,
  "driverId": "driver-user-id"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Payment initiated successfully",
  "data": {
    "paymentId": "payment-uuid",
    "sessionId": "abc123-def456",
    "paymentMethod": "VNPAY",
    "amount": 14000,
    "paymentStatus": "PENDING",
    "transactionCode": "TXN-1234-5678",
    "paymentUrl": "https://sandbox.vnpayment.vn/..."
  }
}
```

---

### 5.3 Staff xác nhận xe ra sau thanh toán điện tử
```
PATCH /api/sessions/{sessionId}/confirm-exit?paymentMethod=VNPAY
Headers: Authorization: Bearer <staff-token>
```

**Path Params:** `sessionId` - ID của parking session

**Query Params:**
- `paymentMethod` (optional): VNPAY, PAYOS, MOMO, CASH

**Response:**
```json
{
  "success": true,
  "message": "Exit confirmed, driver may proceed",
  "data": {
    "sessionId": "abc123-def456",
    "ticketCode": "TKT-xxx",
    "checkoutTime": "2026-06-21T18:05:00",
    "totalFee": 14000,
    "parkingHours": 4,
    "parkingMinutes": 15,
    "sessionStatus": "COMPLETED",
    "paymentStatus": "PAID",
    "paymentMethod": "VNPAY"
  }
}
```

**Lưu ý:**
- Với CASH: Staff gọi `POST /api/sessions/checkout`
- Với VNPay/PayOS/MOMO: Staff gọi `PATCH /api/sessions/{sessionId}/confirm-exit` sau khi payment webhook confirmed

---

## 6) MANAGER APIs

### 6.1 Assign Staff vào Building
PUT `/api/manager/staff/{userId}/buildings`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "buildingIds": ["building-id-1", "building-id-2"]
}
```

### 6.2 Thêm Staff vào Building
POST `/api/manager/buildings/{buildingId}/staff`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "userId": "staff-user-id"
}
```

---

## 7) MANAGER SETUP BUILDING APIs

### 7.1 Tạo Building
POST `/api/manager/setup/buildings`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "buildingName": "Main Parking Building",
  "address": "123 Main Street, District 1",
  "totalFloors": 3,
  "operatingStartTime": "06:00:00",
  "operatingEndTime": "23:00:00",
  "contactNumber": "0909123456"
}
```

### 7.2 Tạo Floor
POST `/api/manager/setup/buildings/{buildingId}/floors`

**Body:**
```json
{
  "floorName": "Floor 1 - Motorbike",
  "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
  "floorLevel": 1,
  "maxCapacity": 50
}
```

### 7.3 Tạo Zone và Slots
POST `/api/manager/setup/floors/{floorId}/zones`

**Body:**
```json
{
  "zoneName": "Zone A",
  "maxCapacity": 20,
  "slotPrefix": "A"
}
```

---

## 8) MANAGER PRICING POLICY APIs

### 8.1 Tạo Pricing Policy
POST `/api/manager/pricing-policy`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "policyName": "Motorbike Standard",
  "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
  "tier1Hours": 2,
  "tier1Price": 5000,
  "tier2Hours": 4,
  "tier2Price": 8000,
  "tier3Hours": 8,
  "tier3Price": 12000,
  "tier4Hours": 12,
  "tier4Price": 15000,
  "perDayPrice": 30000,
  "status": "ACTIVE"
}
```

### 8.2 Xem tất cả Pricing Policies
GET `/api/manager/pricing-policy`

### 8.3 Xem Pricing Policy theo Vehicle Type
GET `/api/manager/pricing-policy/active/{vehicleTypeId}`

---

## 9) USER/DRIVER PROFILE APIs

### 9.1 Xem Profile của tôi
GET `/api/users/me`

**Headers:** `Authorization: Bearer <driver-token>`

### 9.2 Xem Vehicles của tôi
GET `/api/users/me/vehicles`

### 9.3 Thêm Vehicle
POST `/api/users/me/vehicles`

**Body:**
```json
{
  "plateNumber": "51A-12345",
  "vehicleColor": "Black",
  "brand": "Honda",
  "model": "Future",
  "vehicleTypeId": "33333333-3333-3333-3333-333333333331"
}
```

---

## 10) OCR APIs — Nhận diện biển số xe

### 10.1 OCR từ file upload (khuyên dùng cho checkin/checkout)

Staff scan ảnh biển số tại bước checkin/checkout. API này tự động kiểm tra xem biển số có đang có session ACTIVE hay không và trả về cảnh báo.

```
POST /api/ocr/plate/upload
Headers: Authorization: Bearer <staff-token>
Content-Type: multipart/form-data
```

**Body (multipart/form-data):**
- `file` — Ảnh biển số xe (JPEG, PNG, WebP)

**Response (thành công — có phát hiện biển số trùng ACTIVE):**
```json
{
  "plateNumber": "30A-12345",
  "candidates": ["30A-12345", "30A-123.45"],
  "rawText": "30A 12345",
  "normalizedText": "30A 12345",
  "confidence": 0.85,
  "duplicateActiveSession": {
    "plateNumber": "30A-12345",
    "sessionId": "abc123-def456",
    "ticketCode": "G-12345678-ABCD",
    "checkinTime": "2026-07-04T10:00:00",
    "isGuest": true,
    "buildingName": "Main Parking Building",
    "slotName": "A-001"
  }
}
```

**Response (thành công — không trùng):**
```json
{
  "plateNumber": "51H-99999",
  "candidates": ["51H-99999"],
  "rawText": "51H 99999",
  "normalizedText": "51H 99999",
  "confidence": 0.92,
  "duplicateActiveSession": null
}
```

**Response (OCR không đọc được biển số):**
```json
{
  "plateNumber": null,
  "candidates": [],
  "rawText": "NO TEXT FOUND",
  "normalizedText": "",
  "confidence": 0.0,
  "duplicateActiveSession": null
}
```

**Lưu ý:**
- `confidence < 0.5` → nên fallback nhập biển số bằng tay
- `duplicateActiveSession != null` → FE hiện popup cảnh báo trước khi tiếp tục checkin/checkout
- `isGuest: true` → session này là xe vãng lai; `isGuest: false` → xe đặt trước (driver)

---

### 10.2 OCR từ URL

```
POST /api/ocr/plate
Headers: Authorization: Bearer <staff-token>
Content-Type: application/json
```

**Body:**
```json
{
  "imageUrl": "https://example.com/car-plate.jpg"
}
```

**Response:** cùng format với `10.1`

---

### Luồng QUICK CHECKIN (khuyên dùng — không cần nhập tay)

Staff chỉ cần chụp ảnh biển số — hệ thống tự OCR, tự tạo session.

```
1. Staff chụp ảnh biển số xe (hoặc quét từ camera)
2. POST /api/sessions/quick-checkin
   Headers: Authorization: Bearer <staff-token>
   Content-Type: multipart/form-data
   Body: {
     "plateImage": "<file>",
     "buildingId": "building-id",
     "mode": "DRIVER"   // hoặc "GUEST"
   }
   → Hệ thống OCR biển số → tự tìm reservation / assign slot → tạo session
   → Response trả về ticketCode (TKT-xxx hoặc G-xxx)
3. Staff dùng ticketCode để checkout khi xe ra
```

**DRIVER:** Staff quét xe đã đặt trước. Không cần nhập ticketCode.
**GUEST:** Staff quét xe vãng lai. Cần truyền thêm `vehicleTypeId`. Không cần nhập slotId.

---

### Luồng OCR kết hợp checkin/checkout (cách cũ)

#### Checkin có OCR — Driver
```
1. Staff chụp ảnh biển số xe
2. POST /api/ocr/plate/upload  → lấy plateNumber + duplicateActiveSession
3. Nếu duplicateActiveSession != null:
     - isGuest=true: popup "Biển số đang có session ACTIVE. Tiếp tục?"
     - isGuest=false: warning nhẹ để staff đối chiếu với reservation
4. Staff quét ticketCode (từ đặt trước)
5. POST /api/sessions/checkin với plateNumber đã OCR
```

#### Checkin có OCR — Guest (vãng lai)
```
1. Staff chụp ảnh biển số xe
2. POST /api/ocr/plate/upload  → lấy plateNumber + duplicateActiveSession
3. Nếu duplicateActiveSession != null:
     - Popup "Biển số 30A-12345 đang có session ACTIVE
        (mã G-xxx, vào lúc 10:00, tầng 1, slot A-001).
        Vẫn tạo session mới?"
     - Staff xác nhận → tiếp tục
4. POST /api/sessions/guest/checkin với plateNumber đã OCR
```

#### Checkout có OCR
```
1. Staff chụp ảnh biển số xe
2. POST /api/ocr/plate/upload  → lấy plateNumber + duplicateActiveSession
3. Staff quét ticketCode (mã G-xxx hoặc TKT-xxx)
4. Nếu duplicateActiveSession != null VÀ sessionId khác session đang checkout:
     - Popup cảnh báo "Biển số này đang ở session khác"
5. POST /api/sessions/checkout với plateNumber đã OCR
```

---

## 11) QUICK CHECKIN — Staff chỉ quét ảnh, không cần nhập tay

**Core idea:** Staff chỉ cần chụp ảnh biển số xe → hệ thống tự OCR → tự tìm reservation (driver) hoặc tự assign slot (guest) → tự tạo session. Không cần nhập `ticketCode`, không cần nhập `slotId`.

---

### 11.1 Quick Check-in (Driver + Guest) — Staff chỉ quét ảnh

Staff quét ảnh biển số — hệ thống tự nhận diện, tìm reservation / assign slot, tạo session.

```
POST /api/sessions/quick-checkin
Headers: Authorization: Bearer <staff-token>
Content-Type: multipart/form-data
```

**Body (multipart/form-data):**

| Field | Bắt buộc | Mô tả |
|---|---|---|
| `plateImage` | **Có** | Ảnh biển số xe (JPEG/PNG) |
| `buildingId` | **Có** | Building nơi staff đang làm việc |
| `vehicleTypeId` | **Có khi mode=GUEST** | Loại xe (MOTORCYCLE/CAR...) |
| `mode` | Không | `DRIVER` (mặc định) hoặc `GUEST` |

**DRIVER mode — Staff chỉ quét ảnh xe đã đặt trước:**
- Hệ thống OCR biển số từ ảnh
- Tự tìm reservation `PENDING`/`APPROVED` theo biển số + buildingId
- **Validate: biển số quét phải khớp với biển số đăng ký trong reservation**
- Tự tạo ParkingSession, đánh dấu ticket `USED`, slot `OCCUPIED`
- Trả về `ticketCode` (TKT-xxx) để checkout

**GUEST mode — Staff quét xe vãng lai (không đặt trước):**
- Hệ thống OCR biển số từ ảnh
- **Kiểm tra: biển số chưa có session ACTIVE nào trong bãi**
- Tự tìm slot trống đầu tiên theo `buildingId` + `vehicleTypeId`
- Tự tạo Vehicle (reuse nếu đã có), tạo Ticket `G-xxx`, tạo ParkingSession
- Trả về `ticketCode` (G-xxx) để checkout

**Response (thành công — DRIVER):**
```json
{
  "success": true,
  "data": {
    "checkinType": "DRIVER",
    "ticketCode": "TKT-1234567890-456",
    "sessionId": "abc123-def456",
    "plateNumber": "30A-12345",
    "ocrConfidence": 0.85,
    "vehicleColor": "White",
    "brand": "Honda",
    "model": "Future",
    "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
    "vehicleTypeName": "Motorbike",
    "buildingId": "building-id",
    "buildingName": "Main Parking Building",
    "floorId": "floor-1",
    "floorName": "Floor 1 - Motorbike",
    "zoneId": "zone-a",
    "zoneName": "Zone A",
    "slotId": "slot-001",
    "slotName": "A-001",
    "checkinTime": "2026-07-04T14:05:00",
    "basePrice": 5000,
    "hourlyRate": 3000,
    "estimatedFee": 5000,
    "duplicateActiveSession": null
  }
}
```

**Response (thành công — GUEST):**
```json
{
  "success": true,
  "data": {
    "checkinType": "GUEST",
    "ticketCode": "G-1751659789123-4827",
    "sessionId": "xyz789-abc123",
    "plateNumber": "51H-99999",
    "ocrConfidence": 0.92,
    "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
    "vehicleTypeName": "Motorbike",
    "slotName": "B-003",
    "checkinTime": "2026-07-04T14:05:00",
    "basePrice": 5000,
    "estimatedFee": 5000
  }
}
```

**Lỗi thường gặp:**

| Lỗi | Nguyên nhân | Xử lý |
|---|---|---|
| `RESERVATION_NOT_FOUND` | Biển số OCR sai hoặc không có reservation | Staff nhập tay hoặc chuyển GUEST mode |
| `PLATE_MISMATCH` | Biển số quét không khớp biển số đăng ký trong reservation | Staff kiểm tra xe thực tế |
| `PLATE_ALREADY_PARKED` | Biển số đã có session ACTIVE trong bãi (GUEST mode) | Checkout xe đó trước |
| `SLOT_NOT_AVAILABLE` (GUEST) | Không còn slot trống cho loại xe này | Chọn building khác |
| `OCR_FAILED` | Ảnh mờ/không chụp được biển số | Staff chụp lại ảnh rõ hơn |
| `SLOT_NOT_RESERVED` | Slot không ở trạng thái RESERVED | Reservation đã bị hủy hoặc expired |
| `TICKET_ALREADY_USED` | Vé đã được check-in trước đó | Kiểm tra lại mã vé |

**Lưu ý:**
- `ocrConfidence < 0.3` → hệ thống từ chối, yêu cầu chụp lại
- `duplicateActiveSession != null` (DRIVER) → cảnh báo trong response, nhưng vẫn tạo được session
- Staff checkout sau đó: quét ảnh → gọi `/api/ocr/plate/upload` để xác nhận biển số → dùng `ticketCode` trả về để checkout

---

### 11.2 Staff Checkout (Driver — sau Quick Checkin)

```
1. POST /api/ocr/plate/upload  (chụp ảnh biển số khi xe ra)
   → Kiểm tra plateNumber + duplicateActiveSession
2. POST /api/sessions/checkout
   Headers: Authorization: Bearer <staff-token>
   Body: {
     "ticketCode": "TKT-1234567890-456",
     "paymentMethod": "CASH"
   }
```

---

## 12) GUEST OCR CHECKIN / CHECKOUT — Luồng vãng lai đầy đủ

**Core idea:** Staff chỉ cần quét ảnh biển số khi xe vào/ra. Hệ thống tự OCR, tự validate biển số khớp với session.

---

### 12.1 Guest Check-in OCR (Staff quét ảnh → tự động tạo session)

Staff quét ảnh biển số → hệ thống OCR nhận diện → auto-assign slot trống → tạo session.

```
POST /api/sessions/guest/checkin/ocr
Headers: Authorization: Bearer <staff-token>
Content-Type: multipart/form-data
```

**Body (multipart/form-data):**

| Field | Bắt buộc | Mô tả |
|---|---|---|
| `plateImage` | **Có** | Ảnh biển số xe (JPEG/PNG) |
| `buildingId` | **Có** | Building nơi staff đang làm việc |
| `vehicleTypeId` | **Có** | Loại xe (MOTORCYCLE/CAR...) |
| `vehicleColor` | Không | Màu xe |
| `brand` | Không | Hãng xe |
| `model` | Không | Dòng xe |
| `guestName` | Không | Tên khách |
| `guestPhone` | Không | SĐT khách |
| `note` | Không | Ghi chú |
| `checkinImage` | Không | Ảnh check-in (upload lên Cloudinary) |

**Luồng xử lý phía server:**

```
1. OCR biển số từ ảnh (confidence < 0.3 → từ chối)
2. Kiểm tra biển số đã có session ACTIVE trong bãi?
   → Có → Lỗi PLATE_ALREADY_PARKED (không cho checkin)
   → Không → Tiếp tục
3. Tìm slot trống theo buildingId + vehicleTypeId (ưu tiên tầng thấp)
   → Không có slot → Lỗi SLOT_NOT_AVAILABLE
4. Tạo/find Vehicle theo biển số
5. Tạo Ticket G-xxx
6. Tạo ParkingSession
7. Cập nhật slot → OCCUPIED
8. Trả về ticketCode + slot + phí ước tính
```

**Response (thành công):**
```json
{
  "success": true,
  "message": "Guest check-in via OCR successful",
  "data": {
    "sessionId": "xyz789-abc123",
    "ticketCode": "G-1751659789123-4827",
    "guestName": "Nguyen Van A",
    "guestPhone": "0909123456",
    "vehiclePlate": "51H-99999",
    "vehicleColor": "White",
    "brand": "Honda",
    "model": "Future",
    "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
    "vehicleTypeName": "Motorbike",
    "buildingId": "building-id",
    "buildingName": "Main Parking Building",
    "floorId": "floor-1",
    "floorName": "Floor 1 - Motorbike",
    "zoneId": "zone-a",
    "zoneName": "Zone A",
    "slotId": "slot-003",
    "slotName": "A-003",
    "checkinTime": "2026-07-10T22:00:00",
    "checkinImageUrl": "https://cloudinary.com/...",
    "estimatedFee": 5000,
    "basePrice": 5000,
    "hourlyRate": 3000,
    "ocrConfidence": 0.92
  }
}
```

**Response (lỗi PLATE_ALREADY_PARKED):**
```json
{
  "success": false,
  "message": "Biển số 51H-99999 đã đang đỗ trong bãi. Ticket: G-xxx. Vui lòng checkout trước.",
  "error": {
    "code": "PLATE_ALREADY_PARKED",
    "message": "Biển số 51H-99999 đã đang đỗ trong bãi. Ticket: G-xxx. Vui lòng checkout trước."
  }
}
```

**Response (lỗi SLOT_NOT_AVAILABLE):**
```json
{
  "success": false,
  "message": "Không có slot trống nào cho loại xe Motorbike tại building này.",
  "error": {
    "code": "SLOT_NOT_AVAILABLE",
    "message": "Không có slot trống nào cho loại xe Motorbike tại building này."
  }
}
```

---

### 12.2 Guest Checkout OCR (Staff quét ảnh → validate → checkout)

Staff quét ảnh biển số + nhập ticketCode → hệ thống OCR nhận diện → validate biển số khớp → checkout.

```
POST /api/sessions/guest/checkout/ocr
Headers: Authorization: Bearer <staff-token>
Content-Type: multipart/form-data
```

**Body (multipart/form-data):**

| Field | Bắt buộc | Mô tả |
|---|---|---|
| `plateImage` | **Có** | Ảnh biển số xe lúc xe ra (JPEG/PNG) |
| `ticketCode` | **Có** | Mã vé G-xxx (từ checkin) |
| `paymentMethod` | Không | `CASH` (mặc định), `VNPAY`, `PAYOS`, `MOMO` |
| `checkoutImage` | Không | Ảnh check-out (upload lên Cloudinary) |

**Luồng xử lý phía server:**

```
1. Tìm session ACTIVE theo ticketCode
   → Không tìm thấy → Lỗi GUEST_SESSION_NOT_FOUND
2. Verify đây là guest session (không có reservation)
   → Có reservation → Lỗi INVALID_REQUEST
3. OCR biển số từ ảnh (confidence < 0.3 → từ chối)
4. So sánh biển số quét vs biển số trong session
   → Không khớp → Lỗi PLATE_MISMATCH ⚠️
   → Khớp → Tiếp tục
5. Tính phí thực tế (theo thời gian gửi)
6. Tạo Payment (CASH/điện tử)
7. Cập nhật session → COMPLETED, slot → AVAILABLE
8. Trả về totalFee + thông tin checkout
```

**Response (thành công):**
```json
{
  "success": true,
  "message": "Guest checkout via OCR successful",
  "data": {
    "sessionId": "xyz789-abc123",
    "checkoutTime": "2026-07-10T23:30:00",
    "parkingHours": 1,
    "parkingMinutes": 90,
    "basePrice": 5000,
    "hourlyRate": 3000,
    "totalFee": 8000,
    "sessionStatus": "COMPLETED",
    "paymentStatus": "PAID",
    "paymentId": "payment-uuid",
    "paymentMethod": "CASH",
    "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
    "vehicleTypeName": "Motorbike",
    "buildingId": "building-id",
    "buildingName": "Main Parking Building",
    "slotName": "A-003"
  }
}
```

**Response (lỗi PLATE_MISMATCH — biển số quét không khớp):**
```json
{
  "success": false,
  "message": "Biển số quét (51H-99999) không khớp với biển số đăng ký (51H-88888). Kiểm tra lại xe hoặc dùng tìm kiếm thủ công.",
  "error": {
    "code": "PLATE_MISMATCH",
    "message": "Biển số quét (51H-99999) không khớp với biển số đăng ký (51H-88888). Kiểm tra lại xe hoặc dùng tìm kiếm thủ công."
  }
}
```

**Response (lỗi GUEST_SESSION_NOT_FOUND):**
```json
{
  "success": false,
  "message": "Không tìm thấy session ACTIVE cho ticket: G-1751659789123-4827",
  "error": {
    "code": "GUEST_SESSION_NOT_FOUND",
    "message": "Không tìm thấy session ACTIVE cho ticket: G-1751659789123-4827"
  }
}
```

---

### 12.3 Tìm session guest theo biển số

Staff có thể tra cứu session đang hoạt động bằng biển số.

```
GET /api/sessions/guest/plate/{plateNumber}
Headers: Authorization: Bearer <staff-token>
```

Response:
```json
{
  "success": true,
  "message": "Active guest session found",
  "data": {
    "sessionId": "xyz789-abc123",
    "ticketCode": "G-1751659789123-4827",
    "vehiclePlate": "51H-99999",
    "slotName": "A-003",
    "checkinTime": "2026-07-10T22:00:00",
    "estimatedFee": 5000
  }
}
```

---

### 12.4 Tìm session guest theo ticket code

Staff tra cứu session bằng mã vé (trước khi checkout).

```
GET /api/sessions/guest/ticket/{ticketCode}
Headers: Authorization: Bearer <staff-token>
```

---

## 13) QUICK CHECKIN — Staff chỉ quét ảnh, không cần nhập tay

## Parking Session Status (MỚI)
- `CHECKED_IN` - Xe đã vào bãi, đang đỗ
- `CHECKED_OUT` - Xe đã checkout (thanh toán tiền mặt)
- `COMPLETED` - Checkout hoàn tất (thanh toán điện tử)
- `OVERDUE` - Quá giờ đặt chỗ
- `CANCELLED` - Session bị hủy

## Reservation Status (MỚI)
- `PENDING_PAYMENT` - Chờ thanh toán (thay thế PENDING)
- `PAID` - Đã thanh toán (thay thế APPROVED)
- `COMPLETED` - Hoàn tất (đã ra bãi)
- `CANCELLED` - Bị hủy
- `EXPIRED` - Hết hạn (đã thanh toán nhưng không check-in)

## Parking Slot Status
- `AVAILABLE` - Slot trống
- `RESERVED` - Đã được đặt trước
- `OCCUPIED` - Đang có xe đỗ
- `MAINTENANCE` - Đang bảo trì

## Ticket Status
- `ACTIVE` - Vé còn hiệu lực
- `USED` - Đã sử dụng
- `EXPIRED` - Hết hạn
- `LOST` - Mất vé

## Payment Status
- `UNPAID` - Chưa thanh toán
- `PAID` - Đã thanh toán
- `FAILED` - Thanh toán thất bại

---

# LƯU Ý QUAN TRỌNG

1. Staff phải được assign vào building trước khi thao tác
2. Driver đăng ký gửi xe sẽ nhận được ticket_code
3. Driver đưa ticket_code cho staff khi vào/ra bãi
4. Staff check-in/check-out sẽ tự động tính phí theo pricing policy
5. Payment methods: CASH, VNPAY, PAYOS, MOMO
6. Với thanh toán điện tử (VNPAY/PAYOS/MOMO): Staff dùng `PATCH /api/sessions/{sessionId}/confirm-exit` sau khi webhook xác nhận. Với CASH: dùng `POST /api/sessions/checkout`
7. Parking slot status: AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE, PENDING_EXIT

---

# TEST FLOW HOÀN CHỈNH

## A. Driver đăng ký và gửi xe

1. **Login driver:**
   ```
   POST /api/auth/login
   Body: {"email": "driver1@example.com", "password": "1"}
   ```
   → Copy token

2. **Xem slot còn trống:**
   ```
   GET /api/slots/availability
   Headers: Authorization: Bearer <token>
   ```

3. **Đăng ký gửi xe:**
   ```
   POST /api/reservations
   Headers: Authorization: Bearer <token>
   Body: {
     "plateNumber": "51A-12345",
     "vehicleColor": "White",
     "vehicleBrand": "Honda",
     "model": "Future",
     "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
     "buildingId": "building-id"
   }
   ```
   → Copy `ticketCode`

4. **Xem lượt gửi xe của tôi:**
   ```
   GET /api/reservations/me
   Headers: Authorization: Bearer <token>
   ```

## B. Staff check-in và check-out có OCR (CASH)

5. **Login staff:**
   ```
   POST /api/auth/login
   Body: {"email": "staff1@example.com", "password": "123"}
   ```
   → Copy staff token

6. **OCR nhận diện biển số (check-in):**
   ```
   POST /api/ocr/plate/upload
   Headers: Authorization: Bearer <staff-token>
   Content-Type: multipart/form-data
   Body: file=@plate.jpg
   ```
   → Lấy `plateNumber` từ response
   → Nếu `duplicateActiveSession != null`: xử lý cảnh báo

7. **Xác nhận xe vào (check-in):**
   ```
   POST /api/sessions/checkin
   Headers: Authorization: Bearer <staff-token>
   Body: {
     "ticketCode": "<ticketCode-từ-bước-3>",
     "plateNumber": "51A-12345",
     "checkinPlateImage": "data:image/jpeg;base64,...",
     "checkinVehicleImage": "data:image/jpeg;base64,..."
   }
   ```

8. **OCR nhận diện biển số (check-out):**
   ```
   POST /api/ocr/plate/upload
   Headers: Authorization: Bearer <staff-token>
   Content-Type: multipart/form-data
   Body: file=@plate.jpg
   ```
   → Kiểm tra `duplicateActiveSession` trước khi checkout

9. **Xác nhận xe ra (check-out - CASH):**
   ```
   POST /api/sessions/checkout
   Headers: Authorization: Bearer <staff-token>
   Body: {
     "ticketCode": "<ticketCode-từ-bước-3>",
     "paymentMethod": "CASH",
     "checkoutPlateImage": "data:image/jpeg;base64,...",
     "checkoutVehicleImage": "data:image/jpeg;base64,..."
   }
   ```
   → Response sẽ có `totalFee` đã tính, `sessionStatus: "CHECKED_OUT"`

## C. Staff check-in và check-out có OCR (VNPay / PayOS / MOMO)

5. **Login staff:**
   ```
   POST /api/auth/login
   Body: {"email": "staff1@example.com", "password": "123"}
   ```

6. **OCR nhận diện biển số (check-in):**
   ```
   POST /api/ocr/plate/upload
   Headers: Authorization: Bearer <staff-token>
   Content-Type: multipart/form-data
   Body: file=@plate.jpg
   ```

7. **Xác nhận xe vào (check-in):**
   ```
   POST /api/sessions/checkin
   Headers: Authorization: Bearer <staff-token>
   Body: {
     "ticketCode": "<ticketCode>",
     "plateNumber": "51A-12345",
     "checkinPlateImage": "data:image/jpeg;base64,...",
     "checkinVehicleImage": "data:image/jpeg;base64,..."
   }
   ```
   → Copy `sessionId`, `sessionStatus: "CHECKED_IN"`

8. **Dự đoán phí:**
   ```
   GET /api/sessions/estimate?ticketCode=<ticketCode>
   Headers: Authorization: Bearer <staff-token>
   ```

9. **Staff tạo payment link:**
   ```
   POST /api/payments/initiate
   Headers: Authorization: Bearer <staff-token>
   Body: {
     "sessionId": "<sessionId>",
     "paymentMethod": "VNPAY",
     "amount": <totalFee>,
     "driverId": "<driver-user-id>"
   }
   ```

10. **Driver thanh toán** (mở paymentUrl)

11. **OCR nhận diện biển số (check-out):**
    ```
    POST /api/ocr/plate/upload
    Headers: Authorization: Bearer <staff-token>
    Content-Type: multipart/form-data
    Body: file=@plate.jpg
    ```

12. **Staff xác nhận xe ra:**
    ```
    PATCH /api/sessions/<sessionId>/confirm-exit?paymentMethod=VNPAY&lostTicket=false
    Headers: Authorization: Bearer <staff-token>
    ```

## D. Guest (vãng lai) — LUỒNG OCR MỚI (Khuyên dùng)

Staff chỉ quét ảnh biển số → hệ thống tự OCR + validate + checkout.

### D1: Check-in bằng OCR

5. **Login staff:**
   ```
   POST /api/auth/login
   Body: {"email": "staff1@example.com", "password": "123"}
   ```
   → Copy staff token

6. **Guest Check-in OCR:**
   ```
   POST /api/sessions/guest/checkin/ocr
   Headers: Authorization: Bearer <staff-token>
   Content-Type: multipart/form-data
   Body: {
     "plateImage": "<file biển số>",
     "buildingId": "building-id",
     "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
     "vehicleColor": "White",
     "guestName": "Nguyen Van A",
     "guestPhone": "0909123456"
   }
   ```
   → Hệ thống tự OCR biển số → check PLATE_ALREADY_PARKED → assign slot → tạo session
   → Response trả về `ticketCode` (G-xxx) + slot + phí ước tính
   → Copy `ticketCode` để checkout

### D2: Checkout bằng OCR

7. **Guest Checkout OCR:**
   ```
   POST /api/sessions/guest/checkout/ocr
   Headers: Authorization: Bearer <staff-token>
   Content-Type: multipart/form-data
   Body: {
     "plateImage": "<file biển số khi xe ra>",
     "ticketCode": "G-1751659789123-4827",
     "paymentMethod": "CASH"
   }
   ```
   → Hệ thống OCR biển số → so sánh với session → PLATE_MISMATCH nếu không khớp
   → Tính phí thực tế + thanh toán
   → Response: totalFee + checkoutTime + COMPLETED

### D3: Tra cứu trước khi checkout

7a. **Tra cứu session đang hoạt động:**
   ```
   GET /api/sessions/guest/plate/51H-99999
   Headers: Authorization: Bearer <staff-token>
   ```

7b. **Tra cứu session theo ticket code:**
   ```
   GET /api/sessions/guest/ticket/G-1751659789123-4827
   Headers: Authorization: Bearer <staff-token>
   ```

### D4: Guest Quick Check-in (mode=GUEST)

Thay vì endpoint riêng, staff dùng Quick Checkin với `mode=GUEST`:

```
POST /api/sessions/quick-checkin
Headers: Authorization: Bearer <staff-token>
Content-Type: multipart/form-data
Body: {
  "plateImage": "<file biển số>",
  "buildingId": "building-id",
  "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
  "mode": "GUEST"
}
```
→ Tự OCR + tự assign slot + tạo Ticket G-xxx

## F. Manager assign staff

11. **Login manager:**
    ```
    POST /api/auth/login
    Body: {"email": "manager1@example.com", "password": "123"}
    ```

12. **Assign staff vào building:**
    ```
    POST /api/manager/buildings/{buildingId}/staff
    Headers: Authorization: Bearer <manager-token>
    Body: {"userId": "staff-user-id"}
    ```

---

# PRICING REFERENCE

## Car Pricing
- ID: `33333333-3333-3333-3333-333333333332`

| Thời gian gửi | Phí |
|---|---|
| ≤ 2 giờ | 20.000 |
| > 2h - 6h | 40.000 |
| > 6h - 12h | 60.000 |
| > 12h - 24h | 100.000 |
| Mỗi ngày tiếp theo | +100.000 |
| Mất vé | 200.000 |

## Motorbike Pricing
- ID: `33333333-3333-3333-3333-333333333331`

| Thời gian gửi | Phí |
|---|---|
| ≤ 2 giờ | 5.000 |
| > 2h - 6h | 10.000 |
| > 6h - 12h | 15.000 |
| > 12h - 24h | 20.000 |
| Mỗi ngày tiếp theo | +20.000 |
