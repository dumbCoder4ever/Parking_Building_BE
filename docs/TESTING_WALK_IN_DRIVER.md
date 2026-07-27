# Hướng dẫn test tổng thể flow Driver Walk-in

> Tài liệu này hướng dẫn test end-to-end flow **Driver Walk-in Check-in** (nhánh B trong `quickAutoCheckin`).
> Dành cho QA / Frontend dev / Backend dev muốn verify toàn bộ flow từ OCR → tạo session → checkout.

---

## 1. Tổng quan flow

```
┌──────────────┐   POST /api/sessions/checkin    ┌─────────────────────────────┐
│   Staff FE   │  ──────────────────────────────> │ ParkingSessionController    │
│  (chụp ảnh)  │  multipart: plateImage,         │  → ParkingSessionService    │
└──────────────┘    buildingId, vehicleTypeId     │     .quickAutoCheckin()     │
                                                   └─────────────┬───────────────┘
                                                                 │
                              ┌──────────────────────────────────┼──────────────────────────────┐
                              │                                  │                              │
                              ▼                                  ▼                              ▼
                     [A] DRIVER (có reservation)    [B] DRIVER_WALK_IN            [C] GUEST (vãng lai)
                       .quickDriverCheckin()          .quickDriverWalkInCheckin()    .quickGuestCheckin()
                       Ticket: TKT-xxx                Ticket: G-xxx (tạo mới)        Ticket: G-xxx (tạo mới)
                       session.reservation ≠ null    session.user = driver          session.user = null
                       session.user = driver         session.reservation = null
```

**Tài liệu này tập trung vào nhánh [B] DRIVER_WALK_IN** — flow mới thêm ở Phase 1.

### Khi nào rẽ vào nhánh [B]?

| Điều kiện | Giá trị |
|---|---|
| Plate có reservation PENDING/APPROVED trong building? | ❌ KHÔNG |
| Plate thuộc vehicle đã đăng ký với driver? | ✅ CÓ (`vehicle.user != null`) |
| Driver account status | `ACTIVE` |
| Vehicle có active session? | ❌ KHÔNG |
| Có slot AVAILABLE cho `vehicleTypeId`? | ✅ CÓ |
| PricingPolicy cho vehicle type? | ✅ Đã cấu hình |

---

## 2. Chuẩn bị môi trường

### 2.1. Yêu cầu

| Tool | Version |
|---|---|
| JDK | 17+ |
| Maven/Gradle | Gradle wrapper (`./gradlew.bat`) |
| MySQL | 8.x |
| Postman / cURL / Bruno | bất kỳ |
| OCR service | Đang chạy (vd `localhost:8081`) — check `application.yml` |

### 2.2. Chạy backend

```bash
cd c:\SUM26\SWP\Parking_Building_BE
.\gradlew.bat bootRun
```

Backend chạy ở `http://localhost:8080` (mặc định).

### 2.3. Chạy migration DB

Migration walk-in nằm ở `docs/db/DB_MIGRATION_V999.sql`. Các cách apply:

**Cách A — Flyway (khuyến nghị, tự động khi boot):**
- File đã được Spring Boot Flyway tự pick lên khi `bootRun`.
- Kiểm tra log: `Successfully applied X migrations to schema`.

**Cách B — Manual:**
```bash
mysql -u root -p parking_db < docs/db/DB_MIGRATION_V999.sql
```

**Verify schema:**
```sql
USE parking_db;
DESCRIBE parking_sessions;
-- Phải thấy cột `user_id VARCHAR(36) NULL AFTER reservation_id`

SHOW CREATE TABLE parking_sessions\G
-- Phải thấy:
-- CONSTRAINT fk_parking_session_user
--     FOREIGN KEY (user_id) REFERENCES users(user_id)
--     ON DELETE SET NULL

SHOW INDEX FROM parking_sessions WHERE Key_name = 'idx_parking_sessions_user_status';
```

### 2.4. Tạo test data

#### a) Tạo Driver user (nếu chưa có)

```sql
-- User driver ACTIVE
INSERT INTO users (user_id, username, email, full_name, phone_number, role, status, password, created_at, updated_at)
VALUES ('u-driver-test', 'driverTest', 'driver@test.com', 'Nguyen Van Test',
        '0901234567', 'ROLE_DRIVER', 'ACTIVE', '$2a$10$...hash...', NOW(), NOW());
```

#### b) Tạo Vehicle gắn với driver

```sql
INSERT INTO vehicles (vehicle_id, plate_number, user_id, vehicle_type_id, status, created_at, updated_at)
VALUES ('V-test-001', '30A-TEST1', 'u-driver-test', 'VT-CAR', 'ACTIVE', NOW(), NOW());
```

> Lưu ý: biển số trong DB sẽ được normalize uppercase tự động (`30A-TEST1`).

#### c) Tạo Driver account bị khóa (test case DRIVER_ACCOUNT_DEACTIVATED)

```sql
INSERT INTO users (user_id, username, email, full_name, phone_number, role, status, password, created_at, updated_at)
VALUES ('u-driver-locked', 'driverLocked', 'locked@test.com', 'Nguyen Van Locked',
        '0909999999', 'ROLE_DRIVER', 'LOCKED', '$2a$10$...hash...', NOW(), NOW());

INSERT INTO vehicles (vehicle_id, plate_number, user_id, vehicle_type_id, status, created_at, updated_at)
VALUES ('V-test-002', '30A-LOCK1', 'u-driver-locked', 'VT-CAR', 'ACTIVE', NOW(), NOW());
```

#### d) Tạo Staff user + gán vào building

```sql
-- Staff user
INSERT INTO users (user_id, username, email, full_name, role, status, password, created_at, updated_at)
VALUES ('u-staff-test', 'staffTest', 'staff@test.com', 'Staff Test', 'ROLE_STAFF',
        'ACTIVE', '$2a$10$...hash...', NOW(), NOW());

-- Building (giả sử đã có building_id = 'B-1')
-- Gán staff vào building
INSERT INTO building_staff (id, building_id, user_id, created_at)
VALUES ('BS-1', 'B-1', 'u-staff-test', NOW());
```

#### e) Đảm bảo có PricingPolicy cho VT-CAR

```sql
SELECT * FROM pricing_policies WHERE vehicle_type_id = 'VT-CAR' AND status = 'ACTIVE';
-- Nếu rỗng → tạo:
INSERT INTO pricing_policies (policy_id, vehicle_type_id, base_price, hourly_rate, per_day_price, max_hours, status, created_at)
VALUES ('PP-CAR-001', 'VT-CAR', 10000, 5000, 80000, 24, 'ACTIVE', NOW());
```

#### f) Đảm bảo có slot AVAILABLE

```sql
-- Check slot AVAILABLE cho VT-CAR trong building B-1
SELECT ps.slot_id, ps.slot_name, ps.slot_status
FROM parking_slots ps
JOIN zones z ON z.zone_id = ps.zone_id
JOIN floors f ON f.floor_id = z.floor_id
WHERE f.building_id = 'B-1'
  AND f.vehicle_type_id = 'VT-CAR'
  AND ps.slot_status = 'AVAILABLE'
LIMIT 5;
```

Nếu không có, update:
```sql
UPDATE parking_slots SET slot_status = 'AVAILABLE' WHERE slot_id IN ('S-1','S-2','S-3');
```

### 2.5. Chuẩn bị ảnh biển số

OCR cần ảnh rõ nét, biển số nhìn thẳng. Lưu file ảnh:
- `30A-TEST1.jpg` (cho happy case)
- `30A-LOCK1.jpg` (cho case driver locked)
- `30A-MOTORCYCLE.jpg` (cho case vehicleType mismatch — biển của xe CAR nhưng FE gửi vehicleTypeId = MOTORCYCLE)
- `30A-EMPTY.jpg` (cho case OCR fail — ảnh trắng)

> **Tip**: Có thể dùng Google Fonts để in biển số ra giấy rồi chụp ảnh.

### 2.6. Lấy auth token

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"staff@test.com","password":"staff123"}'
```

→ Lưu `accessToken` lại, dùng cho các request dưới.

---

## 3. Test cases

> Tất cả các request dưới đều cần header `Authorization: Bearer <token>`.
> Endpoint chính: `POST http://localhost:8080/api/sessions/checkin` (multipart/form-data).

### 3.1. TC-B01: Happy case — Walk-in thành công

**Mục tiêu**: Xe `30A-TEST1` thuộc driver ACTIVE, không có reservation → flow tạo session DRIVER_WALK_IN.

**Precondition:**
- Driver `driverTest` (status = ACTIVE)
- Vehicle `V-test-001` (plate `30A-TEST1`)
- Có slot AVAILABLE cho VT-CAR
- Ảnh `30A-TEST1.jpg` rõ nét

**Request:**
```bash
curl -X POST http://localhost:8080/api/sessions/checkin \
  -H "Authorization: Bearer <STAFF_TOKEN>" \
  -F "plateImage=@30A-TEST1.jpg" \
  -F "buildingId=B-1" \
  -F "vehicleTypeId=VT-CAR" \
  -F "note=Test walk-in TC-B01"
```

**Expected response:** HTTP 200
```json
{
  "success": true,
  "message": "Check-in successful",
  "data": {
    "checkinType": "DRIVER_WALK_IN",
    "ticketCode": "G-1738012345-a1b2",
    "sessionId": "uuid-session",
    "plateNumber": "30A-TEST1",
    "vehicleTypeId": "VT-CAR",
    "vehicleTypeName": "Car",
    "driverUserId": "u-driver-test",
    "driverUsername": "driverTest",
    "driverFullName": "Nguyen Van Test",
    "driverPhone": "0901234567",
    "driverEmail": "driver@test.com",
    "parkingDuration": 0,
    "sessionStatus": "PENDING_PAYMENT",
    "estimatedFee": 10000
  }
}
```

**Verify trong DB:**
```sql
-- 1. Session có user_id = driver
SELECT session_id, user_id, reservation_id, session_status, payment_status, estimated_fee
FROM parking_sessions WHERE session_id = '<sessionId>';
-- Expected: user_id='u-driver-test', reservation_id=NULL, session_status='PENDING_PAYMENT'

-- 2. Ticket được tạo mới (không gắn reservation)
SELECT ticket_code, reservation_id, status FROM tickets WHERE ticket_code = '<ticketCode>';
-- Expected: reservation_id=NULL, status='ACTIVE'

-- 3. Slot đã được update OCCUPIED
SELECT slot_id, slot_status FROM parking_slots WHERE slot_id = '<slotId>';
-- Expected: slot_status='OCCUPIED'

-- 4. Audit log
SELECT action_type, entity_type, building_id, details
FROM audit_logs WHERE action_type = 'CHECKIN_WALK_IN' ORDER BY created_at DESC LIMIT 1;
-- Expected: details chứa 'Walk-in driver driverTest plate 30A-TEST1'
```

### 3.2. TC-B02: Walk-in thất bại — Driver account bị khóa

**Mục tiêu**: Vehicle thuộc driver LOCKED → throw DRIVER_ACCOUNT_DEACTIVATED, không tạo session.

**Precondition:**
- Driver `driverLocked` (status = LOCKED)
- Vehicle `V-test-002` (plate `30A-LOCK1`)

**Request:**
```bash
curl -X POST http://localhost:8080/api/sessions/checkin \
  -H "Authorization: Bearer <STAFF_TOKEN>" \
  -F "plateImage=@30A-LOCK1.jpg" \
  -F "buildingId=B-1" \
  -F "vehicleTypeId=VT-CAR"
```

**Expected response:** HTTP 400
```json
{
  "success": false,
  "errorCode": "DRIVER_ACCOUNT_DEACTIVATED",
  "message": "Tai khoan driver driverLocked dang LOCKED, khong the check-in walk-in. Vui long lien he quan ly."
}
```

**Verify trong DB:**
```sql
-- Không có session mới được tạo
SELECT COUNT(*) FROM parking_sessions
WHERE vehicle_id = 'V-test-002' AND session_status = 'PENDING_PAYMENT';
-- Expected: 0
```

---

### 3.3. TC-B03: Walk-in thất bại — VehicleTypeId không khớp

**Mục tiêu**: FE gửi `vehicleTypeId = VT-MOTORCYCLE` nhưng biển `30A-TEST1` đăng ký là `VT-CAR` → throw VEHICLE_TYPE_MISMATCH.

**Precondition:** Như TC-B01.

**Request:**
```bash
curl -X POST http://localhost:8080/api/sessions/checkin \
  -H "Authorization: Bearer <STAFF_TOKEN>" \
  -F "plateImage=@30A-TEST1.jpg" \
  -F "buildingId=B-1" \
  -F "vehicleTypeId=VT-MOTORCYCLE"   # <-- sai, đăng ký là VT-CAR
```

**Expected response:** HTTP 400
```json
{
  "success": false,
  "errorCode": "VEHICLE_TYPE_MISMATCH",
  "message": "vehicleTypeId FE gui (VT-MOTORCYCLE) khong khop voi loai xe da dang ky (VT-CAR) cho bien 30A-TEST1"
}
```

---

### 3.4. TC-B04: Walk-in thất bại — Biển đã có session ACTIVE

**Mục tiêu**: Plate `30A-TEST1` đã có session PENDING_PAYMENT → không cho check-in lại.

**Precondition:**
- Đã chạy TC-B01 thành công (session ACTIVE tồn tại)

**Request:** (giống TC-B01)

**Expected response:** HTTP 400
```json
{
  "success": false,
  "errorCode": "PLATE_ALREADY_PARKED",
  "message": "Bien so 30A-TEST1 dang do trong bai. Vui long checkout truoc."
}
```

---

### 3.5. TC-B05: Walk-in thất bại — Hết slot trống

**Mục tiêu**: Tất cả slot cho VT-CAR đều OCCUPIED → throw SLOT_NOT_AVAILABLE.

**Precondition:**
- Set tất cả slot VT-CAR trong B-1 về OCCUPIED:
```sql
UPDATE parking_slots ps
JOIN zones z ON z.zone_id = ps.zone_id
JOIN floors f ON f.floor_id = z.floor_id
SET ps.slot_status = 'OCCUPIED'
WHERE f.building_id = 'B-1' AND f.vehicle_type_id = 'VT-CAR';
```

**Request:** (giống TC-B01)

**Expected response:** HTTP 400
```json
{
  "success": false,
  "errorCode": "SLOT_NOT_AVAILABLE",
  "message": "Khong co slot trong nao cho loai xe Car tai building nay."
}
```

**Restore sau test:**
```sql
UPDATE parking_slots ps
JOIN zones z ON z.zone_id = ps.zone_id
JOIN floors f ON f.floor_id = z.floor_id
SET ps.slot_status = 'AVAILABLE'
WHERE f.building_id = 'B-1' AND f.vehicle_type_id = 'VT-CAR';
```

---

### 3.6. TC-B06: Walk-in thất bại — Staff không assign vào building

**Mục tiêu**: Staff `otherStaff` không có row trong `building_staff` cho B-1 → throw UNAUTHORIZED.

**Precondition:**
- Tạo staff `otherStaff` không assign vào B-1
- Login lấy token của `otherStaff`

**Request:**
```bash
curl -X POST http://localhost:8080/api/sessions/checkin \
  -H "Authorization: Bearer <OTHER_STAFF_TOKEN>" \
  -F "plateImage=@30A-TEST1.jpg" \
  -F "buildingId=B-1" \
  -F "vehicleTypeId=VT-CAR"
```

**Expected response:** HTTP 400
```json
{
  "success": false,
  "errorCode": "UNAUTHORIZED",
  "message": "You are not assigned to this building"
}
```

---

### 3.7. TC-B07: Walk-in thất bại — PricingPolicy chưa cấu hình

**Mục tiêu**: Vehicle type chưa có policy ACTIVE → throw VEHICLE_TYPE_NOT_FOUND (tránh walk-in miễn phí).

**Precondition:**
- Tạm thời disable policy cho VT-CAR:
```sql
UPDATE pricing_policies SET status = 'INACTIVE'
WHERE vehicle_type_id = 'VT-CAR' AND status = 'ACTIVE';
```

**Request:** (giống TC-B01)

**Expected response:** HTTP 400
```json
{
  "success": false,
  "errorCode": "VEHICLE_TYPE_NOT_FOUND",
  "message": "Chua cau hinh pricing policy cho loai xe Car. Vui long lien he quan ly building."
}
```

**Restore:**
```sql
UPDATE pricing_policies SET status = 'ACTIVE'
WHERE vehicle_type_id = 'VT-CAR' AND status = 'INACTIVE';
```

---

### 3.8. TC-B08: Auto flow rẽ nhánh B đúng

**Mục tiêu**: Gọi `quickAutoCheckin` (qua endpoint `/checkin` không truyền ticketCode) mà plate `30A-TEST1` thuộc driver nhưng không có reservation → phải rẽ nhánh B (DRIVER_WALK_IN), không phải nhánh A hay C.

**Precondition:** Như TC-B01 (chưa có session cho biển này).

**Request:**
```bash
curl -X POST http://localhost:8080/api/sessions/checkin \
  -H "Authorization: Bearer <STAFF_TOKEN>" \
  -F "plateImage=@30A-TEST1.jpg" \
  -F "buildingId=B-1" \
  -F "vehicleTypeId=VT-CAR"
# KHÔNG truyền ticketCode — để controller gọi quickAutoCheckin
```

**Verify response:**
- `checkinType = "DRIVER_WALK_IN"` (KHÔNG phải `DRIVER` hay `GUEST`)
- `sessionId` được tạo mới
- `ticketCode` có format `G-xxx`

**Verify DB:** (giống TC-B01)

---

### 3.9. TC-B09: Walk-in từ quickGuestCheckin — biển thuộc driver bị chặn

**Mục tiêu**: Nếu gọi thẳng endpoint guest-checkin mà biển thuộc driver → throw DRIVER_OWNED_PLATE_CANNOT_GUEST_CHECKIN.

> Lưu ý: Trong FE hiện tại chỉ có 1 endpoint `/checkin` gọi `quickAutoCheckin` (tự route). Để test case này, cần tạm gọi qua endpoint riêng nếu có, hoặc test qua unit test (UT-6).

**Cách test thực tế (qua Swagger UI hoặc endpoint guest riêng):**
- Nếu có endpoint `POST /api/sessions/guest-checkin` → gọi với plate `30A-TEST1`.
- Expected: HTTP 400 với `errorCode = DRIVER_OWNED_PLATE_CANNOT_GUEST_CHECKIN`.

**Hoặc test qua unit test:**
```bash
.\gradlew.bat test --tests "ParkingSessionServiceWalkInTest.testGuest_BlockDriverVehicle"
```
→ Expected: PASS

---

### 3.10. TC-B10: Race condition — 2 request đồng thời không pick trùng slot

**Mục tiêu**: Pessimistic lock đảm bảo 2 staff cùng gọi walk-in với 2 biển khác nhau cùng lúc → không pick trùng 1 slot.

**Setup:**
- Có 2 driver + 2 vehicle (`30A-TEST1`, `30A-TEST2`)
- Chỉ có 1 slot AVAILABLE cho VT-CAR

**Request song song (2 terminal / 2 Postman):**
```bash
# Request 1
curl -X POST http://localhost:8080/api/sessions/checkin \
  -H "Authorization: Bearer <STAFF_TOKEN>" \
  -F "plateImage=@30A-TEST1.jpg" -F "buildingId=B-1" -F "vehicleTypeId=VT-CAR" &

# Request 2
curl -X POST http://localhost:8080/api/sessions/checkin \
  -H "Authorization: Bearer <STAFF_TOKEN>" \
  -F "plateImage=@30A-TEST2.jpg" -F "buildingId=B-1" -F "vehicleTypeId=VT-CAR" &

wait
```

**Expected:**
- 1 request → HTTP 200 (session tạo thành công)
- 1 request → HTTP 400 `SLOT_NOT_AVAILABLE` (vì slot đã bị lock bởi request 1)

**Verify DB:**
```sql
SELECT session_id, slot_id, session_status FROM parking_sessions
WHERE session_status = 'PENDING_PAYMENT' ORDER BY created_at DESC LIMIT 2;
-- Expected: 2 session khác nhau, KHÔNG cùng slot_id
```

Nếu cả 2 cùng pick được 1 slot → bug race condition (regression).

---

## 4. Test end-to-end: Walk-in → Checkout

### 4.1. Checkout walk-in session

**Precondition:** Có session walk-in ACTIVE (chạy TC-B01).

**Request:**
```bash
curl -X POST http://localhost:8080/api/sessions/checkout \
  -H "Authorization: Bearer <STAFF_TOKEN>" \
  -F "ticketCode=G-1738012345-a1b2" \
  -F "paymentMethod=CASH" \
  -F "plateImage=@30A-TEST1-out.jpg"
```

**Expected response:** HTTP 200
```json
{
  "success": true,
  "message": "Checkout successful",
  "data": {
    "ticketCode": "G-1738012345-a1b2",
    "sessionStatus": "COMPLETED",
    "paymentStatus": "PAID",
    "totalFee": "<tính theo giờ>",
    "parkingDuration": <số phút>,
    "checkoutTime": "<timestamp>"
  }
}
```

**Verify DB:**
```sql
-- Session đã COMPLETED
SELECT session_status, payment_status, checkout_time, total_fee
FROM parking_sessions WHERE session_id = '<sessionId>';

-- Slot đã về AVAILABLE
SELECT slot_status FROM parking_slots WHERE slot_id = '<slotId>';
```

---

## 5. Test qua Swagger UI

Swagger UI ở: `http://localhost:8080/swagger-ui/index.html`

1. Mở endpoint `POST /api/sessions/checkin`.
2. Authorize với staff token.
3. Fill form (multipart):
   - `plateImage`: chọn file ảnh
   - `buildingId`: `B-1`
   - `vehicleTypeId`: `VT-CAR` (hoặc `VT-MOTORCYCLE` cho TC-B03)
4. Execute.
5. So sánh response với expected.

---

## 6. Test qua unit test (nhanh nhất)

Không cần setup DB/OCR. Chạy trực tiếp:

```bash
.\gradlew.bat test --tests "ParkingSessionServiceWalkInTest"
```

**Expected:** 11/11 PASS (UT-1 → UT-11).

Các test phủ:
- UT-1: Happy case
- UT-2: Driver LOCKED
- UT-3: VehicleTypeId mismatch
- UT-4: Active session exists
- UT-5: No slot available
- UT-6: Guest blocked from driver-owned plate
- UT-7: OCR fail
- UT-8: Auto routes to walk-in
- UT-9: Session persisted with user_id
- UT-10: Staff not assigned
- UT-11: No pricing policy

---

## 7. Checklist khi test

- [ ] Migration V999 đã được apply (FK + index)
- [ ] Driver user ACTIVE đã tạo + gán vehicle
- [ ] Staff user đã tạo + gán vào building
- [ ] Pricing policy ACTIVE cho vehicle type
- [ ] Có slot AVAILABLE cho vehicle type trong building
- [ ] OCR service đang chạy
- [ ] Backend log level ≥ DEBUG (để xem routing)
- [ ] Test 11/11 unit test PASS
- [ ] Smoke test 4 case chính: B01, B04, B08, B10

---

## 8. Troubleshooting

| Vấn đề | Nguyên nhân | Fix |
|---|---|---|
| `OCR_FAILED` | Ảnh rỗng / OCR service down | Upload lại ảnh / check OCR service |
| `UNAUTHORIZED` | Staff không assign vào building | Insert row `building_staff` |
| `SLOT_NOT_AVAILABLE` | Hết slot | Update slot về AVAILABLE |
| `PLATE_ALREADY_PARKED` | Biển đã có session ACTIVE | Checkout session cũ trước |
| `DRIVER_OWNED_PLATE_CANNOT_GUEST_CHECKIN` | Biển thuộc driver | Dùng walk-in flow thay vì guest |
| `VEHICLE_TYPE_NOT_FOUND` (pricing null) | Policy bị INACTIVE hoặc chưa tạo | Activate policy |
| Response chậm > 5s | OCR timeout | Tăng timeout trong `application.yml` |
| `Cannot deserialize instance` | Token hết hạn | Login lại |

---

## 9. Liên kết tham khảo

- Source code: `src/main/java/fpt/swp391/parkingmanagement/service/ParkingSessionService.java`
- Method: `quickAutoCheckin()` (line 1875), `quickDriverWalkInCheckin()` (line 1936)
- Entity: `ParkingSession.java` (field `user` thêm ở line 51)
- Migration: `docs/db/DB_MIGRATION_V999.sql`
- Error codes: `src/main/java/fpt/swp391/parkingmanagement/exception/ErrorCode.java`
- Workflow doc: `docs/BACKEND_WORKFLOW.md` (section "WALK-IN DRIVER CHECK-IN FLOW")
- Unit tests: `src/test/java/fpt/swp391/parkingmanagement/service/ParkingSessionServiceWalkInTest.java`