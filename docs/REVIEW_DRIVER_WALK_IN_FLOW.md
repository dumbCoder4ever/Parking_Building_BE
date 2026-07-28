# DRIVER WALK-IN FLOW — CODE REVIEW

> Status: **REVIEW PENDING** — please verify before fixing

## 1. Tổng quan luồng

```
[Staff quét ảnh biển số]
        |
        v
quickAutoCheckin(staffEmail, req)
        |
        +-- OCR resolve plateNumber
        |
        +-- findPendingReservationsByPlate(normalizedPlate)
        |           [Tìm reservation PENDING/APPROVED]
        |
        +-- filter reservation by buildingId
                |
                v
        [Có reservation] ---> quickDriverCheckin()  [BRANCH A]
        [Không có reservation, có vehicle.user] ---> quickDriverWalkInCheckin()  [BRANCH B]  <-- WALK-IN
        [Không reservation, không vehicle] ---> quickGuestCheckin()  [BRANCH C]
```

## 2. BRANCH B — quickDriverWalkInCheckin()

### 2.1 Các bước thực hiện

| Bước | Code | Mô tả |
|-------|------|-------|
| 0 | `checkStaffBuildingAssignment` | Staff phải được assign vào building |
| 1 | Validate driver != null | Driver phải tồn tại |
| 2 | Validate driver.status == "ACTIVE" | Tài khoản driver phải ACTIVE |
| 3 | Validate vehicleTypeId bắt buộc | FE gửi vehicleTypeId, so sánh với loại xe đã đăng ký |
| 4 | `validateNoActiveSessionForPlate` | Không có session ACTIVE nào cho biển số này |
| 5 | `lockFirstAvailableByBuildingAndVehicleType` | Auto-pick slot trống (ưu tiên tầng thấp, có row lock) |
| 6 | Pricing check | Phải có pricing policy cho loại xe |
| 7 | `estimatedFee = pricingService.calculateByPolicy(policy, 1)` | Tính phí ước lượng (1 giờ) |
| 8 | Tạo Ticket G-xxx | Vé khách lẻ, không gắn reservation |
| 9 | Tạo ParkingSession | **Không gắn reservation**, **Có gắn user=driver** |
| 10 | Update slot -> OCCUPIED | |
| 11 | Audit log | |
| 12 | Build response | |

### 2.2 Dữ liệu được lưu

**ParkingSession:**
```
session.vehicle        = vehicle        ✅ (từ DB query)
session.slot           = slot          ✅ (auto-picked)
session.ticket         = ticket       ✅ (G-xxx mới)
session.user           = driver       ✅ (vehicle.getUser())
session.reservation    = null         ⚠️  (WALK-IN: không có reservation)
session.checkinTime    = now
session.sessionStatus  = "PENDING_PAYMENT"
session.paymentStatus = "UNPAID"
session.estimatedFee   = estimatedFee  ✅ (từ pricing)
```

**TICKET:**
```
ticket.ticketCode = "G-xxx"      ✅
ticket.status    = "ACTIVE"
ticket.isUsed    = false
ticket.isLost    = false
```

### 2.3 Điểm QUAN TRỌNG — Potential Issues

#### ISSUE 1: `session.user` vs `session.reservation` (CRITICAL)

```java
// Line 1992 - Walk-in session
session.setUser(driver);     // ✅ Có gắn user
// session.setReservation(null);  // ⚠️ Không gắn reservation (null mặc định)
```

**Tại sao quan trọng:**
- `DriverServiceImpl.getMyCurrentSessions()` query theo `user.userId`:
  ```java
  List<ParkingSession> sessions = parkingSessionRepository.findAllActiveByUserId(user.getUserId());
  ```
  → Walk-in session CÓ `user` → driver sẽ thấy được current session ✅

- Nhưng `mapToCurrentSession()` KHÔNG lấy vehicle từ `session.vehicle`:
  ```java
  // Line 231-232
  Reservation reservation = session.getReservation();
  Vehicle vehicle = reservation != null ? reservation.getVehicle() : null;
  // ⚠️ reservation = null → vehicle = null
  ```

  → Khi driver xem current session, `vehicle = null` → **không hiển thị được biển số, loại xe!**

#### ISSUE 2: Pricing tính cứng 1 giờ

```java
// Line 1973
BigDecimal estimatedFee = pricingService.calculateByPolicy(policy, 1);
```

→ Luôn tính cho 1 giờ, không tính thực tế.

#### ISSUE 3: Không có trường phân biệt WALK-IN vs RESERVATION

- Không có enum/flag trong ParkingSession để phân biệt "đặt trước" vs "walk-in".
- Rất khó query/filter riêng walk-in sessions.

#### ISSUE 4: `findPendingReservationsByPlate` phạm vi rộng

```java
// Line 1870
List<Reservation> reservations = findPendingReservationsByPlate(normalizedPlate);
```

→ Tìm **TẤT CẢ** reservation PENDING/APPROVED trên **TẤT CẢ** building.
→ Rồi mới filter by building ở bước tiếp theo. Có thể gây chậm nếu có nhiều reservation.

### 2.4 Kiến nghị sửa

1. **`mapToCurrentSession()` phải lấy vehicle từ `session.vehicle`** (thay vì `reservation.vehicle`)
2. Thêm flag `checkinType` vào ParkingSession để phân biệt WALK_IN / RESERVATION
3. Xem xét query reservation có buildingId filter ngay trong repository (thay vì filter sau)

---

## 3. CHECKOUT — driverCheckoutBySession()

### 3.1 Luồng

```
[Driver yêu cầu checkout]
        |
        v
driverCheckoutBySession(staffEmail, sessionId, checkoutImage)
        |
        v
processCheckout(session, staffEmail, checkoutImage, "CASH")
        |
        +-- Tính phí: pricingService.calculateByPolicy(policy, hours)
        |
        +-- Tạo Payment (CASH) -> paymentStatus = "PAID"
        |           session.paymentStatus = "PAID"
        |
        +-- session.sessionStatus = "COMPLETED"
        |
        +-- Update slot -> PENDING_EXIT
        |
        v
CheckoutResponse
```

### 3.2 Điểm QUAN TRỌNG

#### ISSUE 5: Checkout không cần payment trước

```java
// Line 530
session.setPaymentStatus("PAID");
Payment payment = new Payment();
payment.setPaymentMethod("CASH");  // ⚠️ Thanh toán CASH
```

→ Với WALK-IN, checkout = CASH → staff thu tiền mặt → payment ghi nhận → DONE

#### ISSUE 6: Không cần đi qua `/payments/initiate` cho WALK-IN

Đúng với business flow: WALK-IN không cần payment online. Staff checkout trực tiếp.

---

## 4. PAYMENT CHO DRIVER — /users/me/sessions/current

### 4.1 Endpoint

```
GET /api/users/me/sessions/current
Role: DRIVER
```

### 4.2 Code flow

```
DriverServiceImpl.getMyCurrentSessions(email)
        |
        v
parkingSessionRepository.findAllActiveByUserId(userId)
        |
        v
mapToCurrentSession(session, now)
        |
        +-- vehicle = session.reservation?.vehicle  ⚠️ null cho WALK-IN
        +-- pricingService.calculateFee(vehicleTypeId, parkingHours)
        +-- pricingService.resolveStoredSessionFee(session)
        |       → Nếu session.totalFee != null → dùng totalFee
        |       → Nếu null → dùng timeBasedFee
        v
DriverCurrentSessionResponse
```

### 4.3 Vấn đề 404 "No active parking session found"

**Nguyên nhân có thể:**

1. **`findAllActiveByUserId`** — có thể không query đúng:
   - Walk-in session có `session.user = driver` ✅
   - Nhưng nếu `findAllActiveByUserId` query theo `reservation.user_id` thay vì `parking_session.user_id` → sẽ không tìm thấy walk-in session.

2. **Check sessionStatus filter** — walk-in session có `sessionStatus = "PENDING_PAYMENT"`:
   - Nếu `findAllActiveByUserId` chỉ query `sessionStatus = 'ACTIVE'` → WALK-IN sẽ không được tìm thấy.

**Cần xác nhận:** Xem `ParkingSessionRepository.findAllActiveByUserId()` — nó query theo `user_id` nào?

---

## 5. Suggested Fixes (Priority Order)

### Priority 1 — CRITICAL (Frontend 404)
```java
// File: DriverServiceImpl.mapToCurrentSession()
// THAY:
Reservation reservation = session.getReservation();
Vehicle vehicle = reservation != null ? reservation.getVehicle() : null;
// THÀNH:
Vehicle vehicle = session.getVehicle();  // Walk-in: lấy từ session
```

### Priority 2 — HIGH (Check active sessions query)
```java
// File: ParkingSessionRepository.findAllActiveByUserId()
// Xác nhận: Query theo parking_session.user_id (không phải reservation.user_id)
// Xác nhận: sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT') (không chỉ 'ACTIVE')
```

### Priority 3 — MEDIUM (Enhancement)
```java
// Thêm trường checkinType vào ParkingSession entity:
// checkinType = 'RESERVATION' | 'DRIVER_WALK_IN' | 'GUEST'
// Hoặc: session.reservation != null → RESERVATION, null → WALK_IN
```

---

## 6. Questions cần xác nhận từ bạn

1. **Frontend 404**: Endpoint nào bị 404 — `/api/users/me/sessions/current` hay endpoint khác?
2. **Khi nào 404 xảy ra** — ngay sau checkin, hay sau checkout, hay sau một khoảng thời gian?
3. **Driver login**: Driver login = email của user đã được gắn vào vehicle?
4. **Business flow**: Driver walk-in có cần thanh toán online không, hay chỉ CASH?

---

## 7. Một số lưu ý khác

- **Notification đã xóa hoàn toàn** (OK — đúng yêu cầu)
- **WebSocketConfig** đã xóa → WS không còn hoạt động
- **QuickCheckinResponse** trả về đầy đủ thông tin driver (email, phone, vehicle) ✅
- **Code encoding** trong comment có vấn đề UTF-8/CP1252 → một số comment hiển thị sai tiếng Việt
