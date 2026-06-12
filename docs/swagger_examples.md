# Swagger / Try-it examples — Parking Building BE

Base URL: `http://localhost:8080`

---

# CORE FLOW

## FLOW 0 — MANAGER SETUP BUILDING (Tạo cấu trúc bãi xe)

Trước khi User có thể đặt chỗ, Manager cần tạo cấu trúc bãi xe:

### Bước 0.1: Tạo Building
```
POST /api/manager/setup/buildings
```

### Bước 0.2: Tạo Floor (mỗi floor phục vụ 1 loại xe)
```
POST /api/manager/setup/buildings/{buildingId}/floors
```

### Bước 0.3: Tạo Zone và Slots
```
POST /api/manager/setup/floors/{floorId}/zones
```

### Xem danh sách Buildings đã tạo
```
GET /api/manager/setup/buildings
```

### Xem Floors trong Building
```
GET /api/manager/setup/buildings/{buildingId}/floors
```

### Xem Zones trong Floor
```
GET /api/manager/setup/floors/{floorId}/zones
```

### Xem Slots trong Zone
```
GET /api/manager/setup/zones/{zoneId}/slots
```

---

## FLOW 1 — USER ĐẶT TRƯỚC SLOT

### Bước 1: Login
User đăng nhập vào hệ thống.

### Bước 2: Xem slot còn trống
```
GET /api/slots/availability
```
Trả về: Building → Floor → Vehicle Type → Zone → Slots
- Mỗi zone hiển thị: totalSlots, availableSlots
- Ví dụ:
  - Building A → Floor 1 → Motorbike → Zone A (20/20 available), Zone B (15/20 available)
  - Building A → Floor 2 → Car → Zone C (5/10 available)

### Bước 3: Chọn slot và nhập thông tin
- Chọn slot từ danh sách ở Bước 2
- Nhập thông tin xe: biển số, màu xe, hãng xe, model, loại xe
- Chọn thời gian gửi (Ví dụ: 14:00 → 18:00)
- **Hệ thống tự động kiểm tra:**
  - Biển số đã tồn tại → Driver CŨ (cập nhật thông tin xe)
  - Biển số chưa tồn tại → Driver MỚI (tạo xe mới)
  - Trùng thời gian với reservation hiện tại → Báo lỗi

### Bước 4: Tạo Reservation
```
POST /api/reservations
```
Hệ thống:
- tạo reservation
- đổi trạng thái slot: AVAILABLE → RESERVED

### Bước 5: Sinh Ticket
Hệ thống tạo:
- ticket_code
- qr_code
cho user.

---

## FLOW 2 — STAFF XÁC NHẬN XE VÀO BÃI

### Bước 1: User đến bãi xe
User đưa:
- ticket
- QR
- biển số xe

### Bước 2: Staff kiểm tra
Staff đối chiếu:
- ticket
- biển số
- loại xe
- màu xe

### Bước 3: Nếu hợp lệ
```
POST /api/sessions/checkin
```
Hệ thống:
- tạo Parking Session
- cập nhật slot: RESERVED → OCCUPIED
- ghi nhận: checkin_time, slot, vehicle, staff xử lý

### Bước 4: Nếu không hợp lệ
Ví dụ:
- sai biển số
- sai loại xe
- quá giờ reservation
=> staff từ chối check-in.

### Bước 5: Staff xác nhận xe ra
```
POST /api/sessions/checkout
```

---

# API REFERENCE

## 0) Manager Setup Building APIs

### 0.1 Tạo Building
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

### 0.2 Tạo Floor
POST `/api/manager/setup/buildings/{buildingId}/floors`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "floorName": "Floor 1 - Motorbike",
  "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
  "floorLevel": 1,
  "maxCapacity": 50
}
```

**Lưu ý:**
- `vehicleTypeId` lấy từ `GET /api/vehicles/types`
- Motorbike type ID: `33333333-3333-3333-3333-333333333331`
- Car type ID: `33333333-3333-3333-3333-333333333332`

### 0.3 Tạo Zone và Slots
POST `/api/manager/setup/floors/{floorId}/zones`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "zoneName": "Zone A",
  "maxCapacity": 20,
  "slotPrefix": "A"
}
```

Hệ thống sẽ tự động tạo 20 slots với tên: A-001, A-002, ..., A-020

### 0.4 Xem Buildings
GET `/api/manager/setup/buildings`

### 0.5 Xem Floors
GET `/api/manager/setup/buildings/{buildingId}/floors`

### 0.6 Xem Zones
GET `/api/manager/setup/floors/{floorId}/zones`

### 0.7 Xem Slots
GET `/api/manager/setup/zones/{zoneId}/slots`

### 0.8 Cập nhật trạng thái Building
PATCH `/api/manager/setup/buildings/{buildingId}/status`

**Body:**
```json
{
  "status": "ACTIVE"
}
```

**Status values:** ACTIVE, INACTIVE, MAINTENANCE

### 0.9 Cập nhật trạng thái Floor
PATCH `/api/manager/setup/floors/{floorId}/status`

**Body:**
```json
{
  "status": "ACTIVE"
}
```

**Status values:** ACTIVE, INACTIVE, MAINTENANCE

### 0.10 Cập nhật trạng thái Zone
PATCH `/api/manager/setup/zones/{zoneId}/status`

**Body:**
```json
{
  "status": "ACTIVE"
}
```

**Status values:** ACTIVE, INACTIVE, FULL, MAINTENANCE

---

## 1) Chuẩn bị dữ liệu trước khi test

### 1.1 Tạo schema và dữ liệu vền
Chạy SQL theo thứ tự này:

1. `src/main/resources/db/parking_db.sql`
2. `docs/db/add_building_staff.sql`
3. `docs/db/add_floors_slots.sql`

### 1.2 Tài khoản test

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

## 3) FLOW 1 APIs — Đặt trước slot

### 3.1 Xem slot còn trống (theo Building, Vehicle Type)
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
      "floorVehicleTypeId": "motorbike-type",
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
          "slotStatus": "AVAILABLE",
          "vehicleTypeId": "motorbike-type",
          "vehicleTypeName": "Motorbike"
        }
      ]
    }
  ]
}
```

---

### 3.3 Tạo Reservation
POST `/api/reservations`

**Headers:** `Authorization: Bearer <driver-token>`

**Body:**
```json
{
  "slotId": "slot1",
  "plateNumber": "51A-12345",
  "vehicleColor": "White",
  "brand": "Honda",
  "model": "City",
  "vehicleTypeId": "motorbike-type",
  "reservationStart": "2026-06-10T14:00:00",
  "reservationEnd": "2026-06-10T18:00:00"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Reservation created successfully. Please show your ticket when checking in.",
  "data": {
    "reservationId": "res123",
    "reservationCode": "RS-A1B2C3D4",
    "reservationStatus": "PENDING",
    "reservationStart": "2026-06-10T14:00:00",
    "reservationEnd": "2026-06-10T18:00:00",
    "userId": "user123",
    "username": "driver1",
    "vehicleId": "vehicle123",
    "vehiclePlate": "51A-12345",
    "vehicleColor": "White",
    "vehicleBrand": "Honda",
    "vehicleModel": "City",
    "buildingId": "abc123",
    "buildingName": "Main Parking Building",
    "floorId": "floor1",
    "floorName": "Floor 1 - Motorbike",
    "slotId": "slot1",
    "slotName": "A-001",
    "slotStatus": "RESERVED",
    "ticketCode": "T-E5F6G7H8",
    "qrCode": "VC1GLTUtRTUNHN0c4Ig=="
  }
}
```

---

### 3.4 Xem Reservation của tôi
GET `/api/reservations/me`

**Headers:** `Authorization: Bearer <driver-token>`

---

### 3.5) Staff/Manager Reservation APIs

### 3.5.1 Xem tất cả Reservations
GET `/api/staff/reservations`

**Headers:** `Authorization: Bearer <staff-token|manager-token>`

**Query params:**
- `buildingId` — **BẮT BUỘC** với staff, để lọc reservation theo building được assign
- `status` - lọc theo trạng thái
- `page` - trang (default: 0)
- `size` - kích thước trang (default: 20)

**Response bao gồm thông tin xe:** vehiclePlate, vehicleColor, vehicleBrand, vehicleModel - để Staff đối chiếu khi check-in.

### 3.5.2 Xem Reservations theo Status
GET `/api/staff/reservations/by-status`

**Headers:** `Authorization: Bearer <staff-token|manager-token>`

**Query params:**
- `buildingId` — **BẮT BUỘC** với staff
- `status` (PENDING, APPROVED, etc.)

### 3.5.3 Xem Reservation theo ID
GET `/api/staff/reservations/{reservationId}`

**Headers:** `Authorization: Bearer <staff-token|manager-token>`

**Query params:**
- `buildingId` — **BẮT BUỘC** với staff

### 3.5.4 Xem Reservation theo Code
GET `/api/staff/reservations/code/{reservationCode}`

**Headers:** `Authorization: Bearer <staff-token|manager-token>`

**Query params:**
- `buildingId` — **BẮT BUỘC** với staff

---

## 4) FLOW 2 APIs — Staff Check-in/Check-out

### 4.1 Staff Check-in
POST `/api/sessions/checkin`

**Headers:** `Authorization: Bearer <staff-token>`

**Body:**
```json
{
  "ticketCode": "T-E5F6G7H8",
  "qrCode": "VC1GLTUtRTUNH0c4Ig==",
  "plateNumber": "51A-12345",
  "vehicleColor": "White",
  "vehicleTypeId": "motorbike-type",
  "buildingId": "<buildingId>"
}
```

> **Lưu ý:** `buildingId` là **BẮT BUỘC**. Staff phải được assign vào building đó.

**Response (thành công):**
```json
{
  "success": true,
  "message": "Check-in successful",
  "data": {
    "sessionId": "session123",
    "slotId": "slot1",
    "slotName": "A-001",
    "slotStatus": "OCCUPIED",
    "checkinTime": "2026-06-10T14:05:00",
    "vehiclePlateNumber": "51A-12345"
  }
}
```

---

### 4.2 Staff Check-out
POST `/api/sessions/checkout`

**Headers:** `Authorization: Bearer <staff-token>`

**Body:**
```json
{
  "ticketCode": "T-E5F6G7H8",
  "paymentMethod": "CASH",
  "buildingId": "<buildingId>"
}
```

> **Lưu ý:** `buildingId` là **BẮT BUỘC**. Staff phải được assign vào building đó.

**Response:**
```json
{
  "success": true,
  "message": "Check-out successful",
  "data": {
    "sessionId": "session123",
    "checkoutTime": "2026-06-10T17:30:00",
    "totalFee": 15000,
    "paymentId": "payment123"
  }
}
```

---

## 5) Manager APIs

### 5.1 Staff: Duyệt/Cập nhật trạng thái Reservation
PATCH `/api/staff/reservations/{reservationCode}/status`

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

**Lưu ý:** Chỉ STAFF/ADMIN mới được duyệt đơn. MANAGER chỉ có quyền xem.

---

### 5.2 Xem danh sách Drivers
GET `/api/manager/drivers`

**Headers:** `Authorization: Bearer <manager-token>`

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "userId": "uuid",
      "username": "driver1",
      "email": "driver1@example.com",
      "status": "ACTIVE",
      "vehicleCount": 2
    }
  ]
}
```

### 5.3 Xem Vehicles của Driver
GET `/api/manager/drivers/{userId}/vehicles`

**Headers:** `Authorization: Bearer <manager-token>`

### 5.4 Xem Vehicles theo Username
GET `/api/manager/drivers/by-username/{username}/vehicles`

**Headers:** `Authorization: Bearer <manager-token>`

### 5.5 Xem tất cả Vehicles
GET `/api/manager/vehicles`

**Headers:** `Authorization: Bearer <manager-token>`

**Query params:** `plateNumber` (tùy chọn, partial match)

### 5.6 Cập nhật trạng thái Vehicle
PATCH `/api/manager/vehicles/{vehicleId}/status`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "status": "ACTIVE"
}
```

**Status values:** ACTIVE, INACTIVE, BLOCKED

---

## 5.5) Manager Staff Management APIs

### 5.5.1 Xem danh sách Staff
GET `/api/manager/staff`

**Headers:** `Authorization: Bearer <manager-token>`

### 5.5.2 Xem Buildings của Staff
GET `/api/manager/staff/{userId}/buildings`

**Headers:** `Authorization: Bearer <manager-token>`

### 5.5.3 Gán Staff vào nhiều Buildings
PUT `/api/manager/staff/{userId}/buildings`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "buildingIds": ["building-id-1", "building-id-2"]
}
```

### 5.5.4 Xem Staff trong Building
GET `/api/manager/buildings/{buildingId}/staff`

**Headers:** `Authorization: Bearer <manager-token>`

### 5.5.5 Thêm Staff vào Building
POST `/api/manager/buildings/{buildingId}/staff`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "userId": "staff-user-id"
}
```

### 5.5.6 Xóa Staff khỏi Building
DELETE `/api/manager/buildings/{buildingId}/staff/{userId}`

**Headers:** `Authorization: Bearer <manager-token>`

---

## 5.6) Manager Pricing Policy APIs

### 5.6.1 Tạo Pricing Policy
POST `/api/manager/pricing-policy`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "policyName": "Motorbike Standard",
  "vehicleTypeId": "33333333-3333-3333-3333-333333333331",
  "baseFee": 3000,
  "hourlyRate": 2000,
  "flatRate": null,
  "overnightRate": null,
  "overnightStartTime": null,
  "overnightEndTime": null,
  "status": "ACTIVE"
}
```

### 5.6.2 Xem tất cả Pricing Policies
GET `/api/manager/pricing-policy`

**Headers:** `Authorization: Bearer <manager-token>`

### 5.6.3 Xem Pricing Policy theo ID
GET `/api/manager/pricing-policy/{id}`

**Headers:** `Authorization: Bearer <manager-token>`

### 5.6.4 Xem Pricing Policies Active theo Vehicle Type
GET `/api/manager/pricing-policy/active/{vehicleTypeId}`

**Headers:** `Authorization: Bearer <manager-token>`

### 5.6.5 Cập nhật Pricing Policy
PUT `/api/manager/pricing-policy/{id}`

**Headers:** `Authorization: Bearer <manager-token>`

### 5.6.6 Xóa Pricing Policy
DELETE `/api/manager/pricing-policy/{id}`

**Headers:** `Authorization: Bearer <manager-token>`

---

## 7) User/Driver APIs (Profile, Vehicles, Sessions)

> **Lưu ý:** Các API cũ `/api/drivers/*` đã được gộp vào `/api/users/me/*`

### 7.1 Xem Profile của tôi
GET `/api/users/me`

**Headers:** `Authorization: Bearer <driver-token>`

**Response:**
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "username": "Khanh",
    "fullName": "KhanhPHB",
    "email": "Khanh@gmail.com",
    "phoneNumber": null,
    "role": "ROLE_DRIVER",
    "avatarUrl": null,
    "status": "ACTIVE"
  }
}
```

### 7.2 Cập nhật Profile (multipart)
PUT `/api/users/me`

**Headers:** `Authorization: Bearer <driver-token>`
**Content-Type:** `multipart/form-data`

**Body (form-data):**
- `fullName`: string (optional)
- `phoneNumber`: string (optional)
- `avatarUrl`: file (optional)

### 7.3 Đổi Password
PUT `/api/users/me/password`

**Headers:** `Authorization: Bearer <driver-token>`

**Body:**
```json
{
  "currentPassword": "old123",
  "newPassword": "new123"
}
```

---

### 7.4 Xem Vehicles của tôi
GET `/api/users/me/vehicles`

**Headers:** `Authorization: Bearer <driver-token>`

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "vehicleId": 1,
      "plateNumber": "51A-12345",
      "vehicleColor": "Black",
      "brand": "Honda",
      "model": "Future",
      "vehicleTypeName": "Motorbike",
      "status": "ACTIVE"
    }
  ]
}
```

### 7.5 Thêm Vehicle
POST `/api/users/me/vehicles`

**Headers:** `Authorization: Bearer <driver-token>`

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

### 7.6 Cập nhật Vehicle
PUT `/api/users/me/vehicles/{vehicleId}`

**Headers:** `Authorization: Bearer <driver-token>`

**Body:**
```json
{
  "plateNumber": "51A-99999",
  "vehicleColor": "White",
  "brand": "Yamaha",
  "model": "Grande"
}
```

### 7.7 Xóa Vehicle
DELETE `/api/users/me/vehicles/{vehicleId}`

**Headers:** `Authorization: Bearer <driver-token>`

---

### 7.8 Xem lịch sử Parking Sessions
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
      "sessionId": "uuid",
      "vehiclePlateNumber": "51A-12345",
      "vehicleBrand": "Honda",
      "vehicleModel": "Future",
      "slotName": "A-001",
      "zoneName": "Zone A",
      "floorName": "Floor 1 - Motorbike",
      "buildingName": "Main Parking Building",
      "checkinTime": "2026-06-10T14:05:00",
      "checkoutTime": "2026-06-10T17:30:00",
      "duration": "3h 25m",
      "totalFee": 15000,
      "paymentStatus": "PAID",
      "status": "COMPLETED"
    }
  ]
}
```

### 7.9 Xem lịch sử Payments
GET `/api/users/me/payments`

**Headers:** `Authorization: Bearer <driver-token>`

**Query params:**
- `limit` (default: 20)

### 7.10 Xem Statistics (Thống kê cá nhân)
GET `/api/users/me/stats`

**Headers:** `Authorization: Bearer <driver-token>`

**Response:**
```json
{
  "success": true,
  "data": {
    "totalSessions": 25,
    "totalSpent": 375000,
    "totalHours": 120,
    "favoriteSlot": "A-001",
    "favoriteVehicle": "51A-12345"
  }
}
```

---

### 8.1 Xem Profile hiện tại
GET `/api/users/me`

**Headers:** `Authorization: Bearer <token>`

### 8.2 Cập nhật Profile (multipart)
PUT `/api/users/me`

**Headers:** `Authorization: Bearer <token>`
**Content-Type:** `multipart/form-data`

### 8.3 Đổi Password
PUT `/api/users/me/password`

**Headers:** `Authorization: Bearer <token>`

**Body:**
```json
{
  "currentPassword": "old123",
  "newPassword": "new123"
}
```

### 8.4 Tạo User (Admin only)
POST `/api/admin/users`

**Headers:** `Authorization: Bearer <admin-token>`

### 8.5 Cập nhật Role User (Admin only)
PATCH `/api/admin/users/{userId}/role`

**Headers:** `Authorization: Bearer <admin-token>`

**Body:**
```json
{
  "role": "ROLE_STAFF"
}
```

### 8.6 Xóa User (Admin only)
DELETE `/api/admin/users/{userId}`

**Headers:** `Authorization: Bearer <admin-token>`

### 8.7 Xem tất cả Users (Admin only)
GET `/api/admin/users`

**Headers:** `Authorization: Bearer <admin-token>`

### 8.8 Xem User theo ID (Admin only)
GET `/api/admin/users/{userId}`

**Headers:** `Authorization: Bearer <admin-token>`

### 8.9 Cập nhật Status User (Admin only)
PATCH `/api/admin/users/{userId}/status`

**Headers:** `Authorization: Bearer <admin-token>`

**Body:**
```json
{
  "status": "ACTIVE"
}
```

---

## 6) Slot Status

### Xem trạng thái slot cụ thể
GET `/api/slots/{slotId}/status`

**Headers:** `Authorization: Bearer <driver-token|staff-token|manager-token>`

---

# LUỒNG TEST HOÀN CHỈNH

## Test Driver APIs (Profile, Vehicles, Sessions)

1. **Login driver**
   ```
   POST /api/auth/login
   Body: {"email": "driver1@example.com", "password": "1"}
   ```
   → Copy token

2. **Xem Profile của tôi**
   ```
   GET /api/users/me
   Headers: Authorization: Bearer <token>
   ```

3. **Xem Vehicles của tôi**
   ```
   GET /api/users/me/vehicles
   Headers: Authorization: Bearer <token>
   ```

4. **Thêm Vehicle mới**
   ```
   POST /api/users/me/vehicles
   Headers: Authorization: Bearer <token>
   Body: {
     "plateNumber": "51A-99999",
     "vehicleColor": "Black",
     "brand": "Honda",
     "model": "Future",
     "vehicleTypeId": "33333333-3333-3333-3333-333333333331"
   }
   ```

5. **Cập nhật Vehicle**
   ```
   PUT /api/users/me/vehicles/{vehicleId}
   Headers: Authorization: Bearer <token>
   Body: {
     "plateNumber": "51A-88888",
     "vehicleColor": "White"
   }
   ```

6. **Xem lịch sử Parking**
   ```
   GET /api/users/me/sessions
   Headers: Authorization: Bearer <token>
   Query params: limit=20
   ```

7. **Xem lịch sử Payments**
   ```
   GET /api/users/me/payments
   Headers: Authorization: Bearer <token>
   Query params: limit=20
   ```

8. **Xem Statistics**
   ```
   GET /api/users/me/stats
   Headers: Authorization: Bearer <token>
   ```

---

## Test Flow 2: Staff Check-in/Check-out

> **QUAN TRỌNG:** Staff phải được assign vào building trước khi thao tác. Xem **FLOW 3** bên dưới.

6. **Login staff**
   ```
   POST /api/auth/login
   Body: {"email": "staff1@example.com", "password": "123"}
   ```
   → Copy token

7. **Staff check-in**
   ```
   POST /api/sessions/checkin
   Headers: Authorization: Bearer <staff-token>
   Body: {
     "ticketCode": "<ticketCode-từ-bước-5>",
     "plateNumber": "51A-99999",
     "vehicleColor": "Black",
     "vehicleTypeId": "<vehicleTypeId>",
     "buildingId": "<buildingId-mà-staff-được-assign>"
   }
   ```

8. **Staff check-out**
   ```
   POST /api/sessions/checkout
   Headers: Authorization: Bearer <staff-token>
   Body: {
     "ticketCode": "<ticketCode-từ-bước-5>",
     "paymentMethod": "CASH",
     "buildingId": "<buildingId-mà-staff-được-assign>"
   }
   ```

---

# STATUS VALUES

- **Parking slot:** AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE
- **Reservation:** PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED, COMPLETED
- **Ticket:** ACTIVE, USED, EXPIRED, LOST
- **Parking session:** ACTIVE, COMPLETED, CANCELLED
- **Parking session payment:** UNPAID, PAID, FAILED
- **Payment:** SUCCESS, FAILED, PENDING

---

# LƯU Ý QUAN TRỌNG

1. Check-in yêu cầu reservation phải ở trạng thái **APPROVED**
2. Sau khi tạo reservation, trạng thái là **PENDING**
3. Staff phải được assign vào building trước khi thao tác reservation/session
4. Staff xin gia nhập building → Manager duyệt → Tự động assign
5. Staff có thể thuộc nhiều buildings cùng lúc
6. Khi gọi staff reservation/session APIs, luôn truyền `buildingId` trong query/body

---

# FLOW 3 — STAFF XIN GIA NHẬP BUILDING

## Nghiệp vụ

Staff phải **xin gia nhập building** → Manager **duyệt** → Staff mới thấy được reservations/checkin/checkout ở building đó. Tương tự như xin ứng tuyển và được tuyển vào công ty.

---

## 3.1 Staff xem danh sách buildings

```
GET /api/manager/setup/buildings
Headers: Authorization: Bearer <manager-token|staff-token>
```

---

## 3.2 Staff xin gia nhập 1 building

```
POST /api/staff/building-requests
Headers: Authorization: Bearer <staff-token>
Body: {
  "buildingId": "<buildingId-muốn-xin>"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "requestId": "<requestId>",
    "buildingId": "B001",
    "buildingName": "Main Parking Building",
    "status": "PENDING",
    "requestedAt": "2026-06-12T17:00:00",
    "requesterUserId": "<staff-userId>",
    "requesterUsername": "staff1",
    "requesterFullName": "Staff One"
  }
}
```

---

## 3.3 Staff xem request của mình

```
GET /api/staff/building-requests
Headers: Authorization: Bearer <staff-token>
```

---

## 3.4 Staff xem buildings đã được assign

```
GET /api/staff/buildings
Headers: Authorization: Bearer <staff-token>
```

---

## 3.5 Manager xem các request đang chờ

```
GET /api/manager/building-requests
Headers: Authorization: Bearer <manager-token>
```

---

## 3.6 Manager xem requests của 1 building

```
GET /api/manager/buildings/{buildingId}/building-requests
Headers: Authorization: Bearer <manager-token>
```

---

## 3.7 Manager duyệt request

```
POST /api/manager/building-requests/{requestId}/approve
Headers: Authorization: Bearer <manager-token>
```

Sau khi duyệt, staff sẽ được **tự động assign** vào building đó.

**Nếu từ chối:**

```
POST /api/manager/building-requests/{requestId}/reject
Headers: Authorization: Bearer <manager-token>
Query: reason=<lý-do-từ-chối>
```

---

## 3.8 Staff thao tác reservation ở building được assign

```
GET /api/staff/reservations?buildingId=<buildingId>
Headers: Authorization: Bearer <staff-token>
```

**Staff xem reservations theo trạng thái:**

```
GET /api/staff/reservations/by-status?buildingId=<buildingId>&status=PENDING
Headers: Authorization: Bearer <staff-token>
```

**Staff duyệt reservation:**

```
PATCH /api/staff/reservations/{reservationCode}/status?buildingId=<buildingId>
Headers: Authorization: Bearer <staff-token>
Body: {"status": "APPROVED"}
```

**Staff xem chi tiết reservation:**

```
GET /api/staff/reservations/{reservationId}?buildingId=<buildingId>
GET /api/staff/reservations/code/{reservationCode}?buildingId=<buildingId>
Headers: Authorization: Bearer <staff-token>
```

---

## StaffBuildingRequest Status Values

- **PENDING** — đang chờ duyệt
- **APPROVED** — được duyệt, staff đã được assign vào building
- **REJECTED** — bị từ chối

---

## Test Flow 3 Đầy Đủ

**Giả định:** Có `staff1@example.com` (ROLE_STAFF) và `manager1@example.com` (ROLE_MANAGER), building `B001` đã tạo ở FLOW 0.

### A. Staff chưa được assign → không thấy reservations

1. **Login staff:**
   ```
   POST /api/auth/login
   Body: {"email": "staff1@example.com", "password": "123"}
   ```
   → Token: `<staff-token>`

2. **Staff xem buildings đã được assign:**
   ```
   GET /api/staff/buildings
   Headers: Authorization: Bearer <staff-token>
   ```
   → `[]` (danh sách rỗng)

3. **Thử xem reservations (sẽ lỗi 403):**
   ```
   GET /api/staff/reservations?buildingId=B001
   Headers: Authorization: Bearer <staff-token>
   ```
   → `403: You are not assigned to this building. Please request to join it first.`

4. **Thử check-in (sẽ lỗi 403):**
   ```
   POST /api/sessions/checkin
   Headers: Authorization: Bearer <staff-token>
   Body: {"ticketCode": "T-ABC123", "buildingId": "B001"}
   ```
   → `403: You are not assigned to this building.`

### B. Staff xin gia nhập building

5. **Staff xem danh sách buildings (để lấy buildingId):**
   ```
   GET /api/manager/setup/buildings
   Headers: Authorization: Bearer <staff-token>
   ```
   → Copy `<buildingId>` (ví dụ: `B001`)

6. **Staff xin gia nhập:**
   ```
   POST /api/staff/building-requests
   Headers: Authorization: Bearer <staff-token>
   Body: {"buildingId": "B001"}
   ```
   → Response: `{"status": "PENDING", "requestId": "<requestId>"}`

7. **Staff xem request của mình:**
   ```
   GET /api/staff/building-requests
   Headers: Authorization: Bearer <staff-token>
   ```
   → Thấy 1 request, status `PENDING`

### C. Manager duyệt request

8. **Login manager:**
   ```
   POST /api/auth/login
   Body: {"email": "manager1@example.com", "password": "123"}
   ```
   → Token: `<manager-token>`

9. **Manager xem request đang chờ:**
   ```
   GET /api/manager/building-requests
   Headers: Authorization: Bearer <manager-token>
   ```
   → Thấy request của `staff1@example.com` với `buildingId: "B001"`, copy `<requestId>`

10. **Manager duyệt:**
    ```
    POST /api/manager/building-requests/<requestId>/approve
    Headers: Authorization: Bearer <manager-token>
    ```
    → Response: `{"status": "APPROVED"}`

### D. Staff sau khi được duyệt → thao tác được

11. **Staff xem buildings đã được assign:**
    ```
    GET /api/staff/buildings
    Headers: Authorization: Bearer <staff-token>
    ```
    → Thấy `B001` trong danh sách

12. **Staff xem reservations ở B001:**
    ```
    GET /api/staff/reservations?buildingId=B001
    Headers: Authorization: Bearer <staff-token>
    ```
    → Thành công, trả về danh sách reservations

13. **Staff duyệt reservation:**
    ```
    PATCH /api/staff/reservations/<code>/status?buildingId=B001
    Headers: Authorization: Bearer <staff-token>
    Body: {"status": "APPROVED"}
    ```

14. **Staff check-in:**
    ```
    POST /api/sessions/checkin
    Headers: Authorization: Bearer <staff-token>
    Body: {
      "ticketCode": "T-ABC123",
      "buildingId": "B001"
    }
    ```

15. **Staff check-out:**
    ```
    POST /api/sessions/checkout
    Headers: Authorization: Bearer <staff-token>
    Body: {
      "ticketCode": "T-ABC123",
      "paymentMethod": "CASH",
      "buildingId": "B001"
    }
    ```

---

## Lỗi Thường Gặp

| Lỗi | Nguyên nhân | Cách fix |
|------|-------------|---------|
| `403: You are not assigned to this building` | Staff chưa được assign vào building | Làm FLOW 3 để được assign |
| `403: Access denied` | Token không phải staff/manager | Kiểm tra role của user đang login |
| `404: Reservation not found` | Sai `buildingId` hoặc `reservationId` | Truyền đúng `buildingId` |
| `400: Ticket not found` | `ticketCode` không đúng | Copy đúng ticketCode từ bước reservation |
| `409: Duplicate` | Request đã tồn tại | Thử building khác hoặc đợi duyệt request cũ |
| `400: This request has already been reviewed` | Request không còn PENDING | Chỉ review request PENDING |
