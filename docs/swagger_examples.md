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

### 1.1 Tạo schema và dữ liệu nền
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
    "slotId": "slot1",
    "slotName": "A-001",
    "slotStatus": "RESERVED",
    "buildingId": "abc123",
    "buildingName": "Main Parking Building",
    "floorId": "floor1",
    "floorName": "Floor 1 - Motorbike",
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
  "vehicleTypeId": "motorbike-type"
}
```

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
  "paymentMethod": "CASH"
}
```

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

### 5.1 Cập nhật trạng thái Reservation
PATCH `/api/manager/reservations/{reservationCode}/status`

**Headers:** `Authorization: Bearer <manager-token>`

**Body:**
```json
{
  "status": "APPROVED",
  "note": "Approved"
}
```

**Status values:** PENDING, APPROVED, REJECTED, CANCELLED, COMPLETED

---

## 6) Slot Status

### Xem trạng thái slot cụ thể
GET `/api/slots/{slotId}/status`

**Headers:** `Authorization: Bearer <driver-token|staff-token|manager-token>`

---

# LUỒNG TEST HOÀN CHỈNH

## Test Flow 1: User đặt trước slot

1. **Login driver**
   ```
   POST /api/auth/login
   Body: {"email": "driver1@example.com", "password": "1"}
   ```
   → Copy token

2. **Xem slot còn trống**
   ```
   GET /api/slots/availability
   Headers: Authorization: Bearer <token>
   ```
   → Xem building, floor, vehicle type, zone, slots

3. **Xem vehicle types**
   ```
   GET /api/vehicles/types
   Headers: Authorization: Bearer <token>
   ```
   → Copy vehicleTypeId

4. **Tạo Reservation**
   ```
   POST /api/reservations
   Headers: Authorization: Bearer <token>
   Body: {
     "slotId": "<slot-id>",
     "plateNumber": "51A-99999",
     "vehicleColor": "Black",
     "brand": "Yamaha",
     "model": "Future",
     "vehicleTypeId": "<vehicleTypeId>",
     "reservationStart": "2026-06-10T14:00:00",
     "reservationEnd": "2026-06-10T18:00:00"
   }
   ```
   → Hệ thống tự kiểm tra biển số (driver cũ/mới) và trùng thời gian
   → Copy reservationCode và ticketCode

## Test Flow 2: Staff Check-in/Check-out

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
     "vehicleTypeId": "<vehicleTypeId>"
   }
   ```

8. **Staff check-out**
   ```
   POST /api/sessions/checkout
   Headers: Authorization: Bearer <staff-token>
   Body: {
     "ticketCode": "<ticketCode-từ-bước-5>",
     "paymentMethod": "CASH"
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
3. Cần staff/manager đổi sang **APPROVED** trước khi check-in
4. Hoặc có thể dùng manager approve trước:
   ```
   PATCH /api/manager/reservations/{reservationCode}/status
   Body: {"status": "APPROVED"}
   ```
