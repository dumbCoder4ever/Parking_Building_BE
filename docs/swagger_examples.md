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

### Check-in xe:
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

### Check-out xe (CASH):
```
POST /api/sessions/checkout
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

# STATUS VALUES

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

## B. Staff check-in và check-out (CASH)

5. **Login staff:**
   ```
   POST /api/auth/login
   Body: {"email": "staff1@example.com", "password": "123"}
   ```
   → Copy staff token

6. **Xác nhận xe vào (check-in):**
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

7. **Xác nhận xe ra (check-out - CASH):**
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

## C. Staff check-in và check-out (VNPay / PayOS / MOMO)

5. **Login staff:**
   ```
   POST /api/auth/login
   Body: {"email": "staff1@example.com", "password": "123"}
   ```

6. **Xác nhận xe vào (check-in):**
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

7. **Dự đoán phí:**
   ```
   GET /api/sessions/estimate?ticketCode=<ticketCode>
   Headers: Authorization: Bearer <staff-token>
   ```

8. **Staff tạo payment link:**
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

9. **Driver thanh toán** (mở paymentUrl)

10. **Staff xác nhận xe ra:**
    ```
    PATCH /api/sessions/<sessionId>/confirm-exit?paymentMethod=VNPAY&lostTicket=false
    Headers: Authorization: Bearer <staff-token>
    ```

## D. Manager assign staff

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
