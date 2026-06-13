# Swagger / Try-it examples — Parking Building BE

Base URL: `http://localhost:8080`

---

# LUỒNG MỚI (KHÔNG CÓ RESERVATION ĐẶT TRƯỚC)

## FLOW DRIVER - GỬI XE THEO LƯỢT

### Bước 1: Login
Driver đăng nhập vào hệ thống.

### Bước 2: Xem thông tin bãi xe
```
GET /api/buildings/{buildingId}
```

### Bước 3: Xem slot còn trống
```
GET /api/slots/availability
```
Trả về: Building → Floor → Vehicle Type → Zone → Slots

### Bước 4: Đăng ký gửi xe (Tạo lượt gửi)
```
POST /api/reservations
```
Hệ thống:
- Tạo ticket_code cho driver
- Đổi trạng thái slot: AVAILABLE → RESERVED

### Bước 5: Driver đến bãi xe
Driver đưa:
- ticket_code
- biển số xe

### Bước 6: Staff check-in xe
Staff kiểm tra và xác nhận:
```
POST /api/sessions/checkin
```
Hệ thống:
- Tạo Parking Session
- Cập nhật slot: RESERVED → OCCUPIED
- Ghi nhận: checkin_time, slot, vehicle, staff xử lý

### Bước 7: Driver lấy xe và thanh toán
Staff xác nhận xe ra:
```
POST /api/sessions/checkout
```
Hệ thống:
- Tính phí theo thời gian gửi
- Thu phí (CASH, VNPAY, PAYOS, MOMO)
- Cập nhật slot: OCCUPIED → AVAILABLE

---

## FLOW STAFF - XỬ LÝ XE VÀO/RA

### Check-in xe:
```
POST /api/sessions/checkin
Body: {
  "ticketCode": "TKT-xxx",
  "plateNumber": "51A-12345",
  "buildingId": "building-id"
}
```

### Check-out xe:
```
POST /api/sessions/checkout
Body: {
  "ticketCode": "TKT-xxx",
  "paymentMethod": "CASH",
  "buildingId": "building-id"
}
```

### Xem session đang hoạt động:
```
GET /api/staff/sessions/active?buildingId=xxx
```

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
  "buildingId": "building-id",
  "slotId": "slot-id"  // optional - nếu không truyền sẽ tự động tìm slot
}
```

**Response:**
```json
{
  "success": true,
  "message": "Check-in successful",
  "data": {
    "sessionId": "session123",
    "ticketCode": "TKT-xxx",
    "plateNumber": "51A-12345",
    "vehicleTypeName": "Motorbike",
    "buildingName": "Main Parking Building",
    "floorName": "Floor 1",
    "zoneName": "Zone A",
    "slotName": "A-001",
    "checkinTime": "2026-06-10T14:05:00",
    "sessionStatus": "ACTIVE",
    "paymentStatus": "UNPAID",
    "estimatedFee": 3000
  }
}
```

---

### 4.2 Check-out xe (Thu phí)
POST `/api/sessions/checkout`

**Headers:** `Authorization: Bearer <staff-token>`

**Body:**
```json
{
  "ticketCode": "TKT-xxx",
  "paymentMethod": "CASH",
  "buildingId": "building-id",
  "note": "Optional note"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Checkout successful. Payment completed.",
  "data": {
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
    "parkingHours": 4,
    "parkingMinutes": 205,
    "sessionStatus": "COMPLETED",
    "paymentStatus": "PAID",
    "totalFee": 12000,
    "paymentId": "payment123",
    "paymentMethod": "CASH",
    "basePrice": 3000,
    "hourlyRate": 2000,
    "pricingTiers": [
      {"tierLabel": "≤ 2h", "maxHours": 2, "price": 5000},
      {"tierLabel": "≤ 4h", "maxHours": 4, "price": 8000},
      {"tierLabel": "≤ 8h", "maxHours": 8, "price": 12000}
    ],
    "feeExplanation": "4h parking: ≤ 8h = 12000 VND"
  }
}
```

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

### 4.8 Xem sessions đang hoạt động
GET `/api/staff/sessions/active`

**Headers:** `Authorization: Bearer <staff-token>`

**Query params:**
- `buildingId` — để lọc theo building

---

### 4.9 Xem sessions theo Status
GET `/api/staff/sessions`

**Headers:** `Authorization: Bearer <staff-token>`

**Query params:**
- `buildingId` — để lọc theo building
- `status` (ACTIVE, COMPLETED, etc.)

---

## 5) MANAGER APIs

### 5.1 Assign Staff vào Building
PUT `/api/manager/staff/{userId}/buildings`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "buildingIds": ["building-id-1", "building-id-2"]
}
```

### 5.2 Thêm Staff vào Building
POST `/api/manager/buildings/{buildingId}/staff`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "userId": "staff-user-id"
}
```

---

## 6) MANAGER SETUP BUILDING APIs

### 6.1 Tạo Building
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

### 6.2 Tạo Floor
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

### 6.3 Tạo Zone và Slots
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

## 7) MANAGER PRICING POLICY APIs

### 7.1 Tạo Pricing Policy
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

### 7.2 Xem tất cả Pricing Policies
GET `/api/manager/pricing-policy`

### 7.3 Xem Pricing Policy theo Vehicle Type
GET `/api/manager/pricing-policy/active/{vehicleTypeId}`

---

## 8) USER/DRIVER PROFILE APIs

### 8.1 Xem Profile của tôi
GET `/api/users/me`

**Headers:** `Authorization: Bearer <driver-token>`

### 8.2 Xem Vehicles của tôi
GET `/api/users/me/vehicles`

### 8.3 Thêm Vehicle
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

- **Parking slot:** AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE
- **Parking registration:** PENDING, APPROVED, REJECTED, CANCELLED, COMPLETED
- **Ticket:** ACTIVE, USED, EXPIRED, LOST
- **Parking session:** ACTIVE, COMPLETED, CANCELLED
- **Parking session payment:** UNPAID, PAID, FAILED
- **Payment:** SUCCESS, FAILED, PENDING

---

# LƯU Ý QUAN TRỌNG

1. Staff phải được assign vào building trước khi thao tác
2. Driver đăng ký gửi xe sẽ nhận được ticket_code
3. Driver đưa ticket_code cho staff khi vào/ra bãi
4. Staff check-in/check-out sẽ tự động tính phí theo pricing policy
5. Payment methods: CASH, VNPAY, PAYOS, MOMO

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

## B. Staff check-in và check-out

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
     "buildingId": "building-id"
   }
   ```

7. **Xác nhận xe ra (check-out - thu phí):**
   ```
   POST /api/sessions/checkout
   Headers: Authorization: Bearer <staff-token>
   Body: {
     "ticketCode": "<ticketCode-từ-bước-3>",
     "paymentMethod": "CASH",
     "buildingId": "building-id"
   }
   ```
   → Response sẽ có `totalFee` đã tính

## C. Manager assign staff

8. **Login manager:**
   ```
   POST /api/auth/login
   Body: {"email": "manager1@example.com", "password": "123"}
   ```
   → Copy manager token

9. **Assign staff vào building:**
   ```
   POST /api/manager/buildings/{buildingId}/staff
   Headers: Authorization: Bearer <manager-token>
   Body: {"userId": "staff-user-id"}
   ```
