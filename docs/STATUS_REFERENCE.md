# Thống kê Status trong Project Parking Management

Tài liệu này tổng hợp **tất cả các trạng thái (status)** được sử dụng trong project, kèm vị trí khai báo, entity sử dụng và logic chuyển trạng thái.

> **Cập nhật:** 2026-07-01 — quét toàn bộ `src/main/java` của module `Parking_Building_BE`.

---

## Tổng quan nhanh

| # | Tên status | Loại | Nơi dùng | Số giá trị |
|---|---|---|---|---|
| 1 | `SessionStatus` | Enum | ParkingSession | 5 |
| 2 | `SessionPaymentStatus` | Enum | (DTO/Util) | 3 |
| 3 | `PaymentStatus` | Enum | (DTO/Util) | 5 |
| 4 | `PaymentMethod` | Enum | Payment | 5 |
| 5 | `PaidStatusFilter` | Enum | Filter API | 3 |
| 6 | `PaidStatus` | Enum | (DTO/Util) | 2 |
| 7 | `ConfirmationStatus` | Enum | (DTO/Util) | 2 |
| 8 | `slotStatus` (ParkingSlot) | String | ParkingSlot | 4 |
| 9 | `reservationStatus` (Reservation) | String | Reservation | 8 |
| 10 | `status` (User) | String | User | 3 |
| 11 | `status` (Vehicle) | String | Vehicle | 3 |
| 12 | `status` (Ticket) | String | Ticket | 2 |
| 13 | `status` (Building) | String | Building | 3 |
| 14 | `status` (Floor) | String | Floor | 3 |
| 15 | `status` (Zone) | String | Zone | 4 |
| 16 | `status` (PricingPolicy) | String | PricingPolicy | 2 |
| 17 | `sessionStatus` (ParkingSession) | String | ParkingSession | (= SessionStatus) |
| 18 | `paymentStatus` (ParkingSession) | String | ParkingSession | 3 |

---

## 1. SessionStatus (Enum) — Vòng đời phiên đỗ xe

**File:** `enums/SessionStatus.java`

| Giá trị | Mô tả | Set ở đâu |
|---|---|---|
| `ACTIVE` | Đang gửi (đã check-in) | `ParkingSessionService` check-in |
| `PENDING_PAYMENT` | Chờ thanh toán | Khi yêu cầu checkout |
| `PENDING_EXIT` | Đã trả tiền, chờ ra cổng | Sau khi thanh toán thành công |
| `COMPLETED` | Hoàn tất (đã ra) | Sau khi xác nhận ra cổng |
| `CANCELLED` | Đã hủy | Khi staff hủy |

**State machine:**
```
PENDING_PAYMENT ──► PENDING_EXIT ──► COMPLETED
       │
       └────────► CANCELLED
ACTIVE ──────────────────────────────────────┘ (khi cancel)
```

> ⚠️ **Lưu ý:** Entity `ParkingSession` dùng `sessionStatus` (String) nhưng **không dùng enum này** — nó dùng giá trị String tự do. Nên đồng bộ hóa với enum này.

---

## 2. SessionPaymentStatus (Enum)

**File:** `enums/SessionPaymentStatus.java`

| Giá trị | Mô tả |
|---|---|
| `UNPAID` | Phiên chưa thanh toán |
| `PAID` | Đã thanh toán |
| `FAILED` | Thanh toán lỗi |

---

## 3. PaymentStatus (Enum)

**File:** `enums/PaymentStatus.java`

| Giá trị | Mô tả |
|---|---|
| `PENDING` | Đang xử lý (chờ callback cổng thanh toán) |
| `PAID` | Đã trả tiền |
| `CONFIRMED` | Đã xác nhận (do staff duyệt) |
| `FAILED` | Thất bại |
| `SUCCESS` | Thành công (alias của PAID/CONFIRMED) |

> ⚠️ **Trùng lặp logic:** Có cả `PAID`, `CONFIRMED` và `SUCCESS` — cần thống nhất 1 quy ước. Hiện code thực tế chỉ dùng String `"PENDING"`, `"PAID"`, `"FAILED"`.

---

## 4. PaymentMethod (Enum)

**File:** `enums/PaymentMethod.java`

| Giá trị | Mô tả |
|---|---|
| `CASH` | Tiền mặt tại quầy |
| `BANKING` | Chuyển khoản ngân hàng |
| `MOMO` | Ví MoMo |
| `VNPAY` | Cổng VNPay |
| `PAYOS` | Cổng PayOS |

---

## 5. PaidStatusFilter (Enum) — Filter trong query API

**File:** `enums/PaidStatusFilter.java`

| Giá trị | Mô tả |
|---|---|
| `PAID` | Lọc các session/payment đã trả |
| `UNPAID` | Lọc các session chưa trả |
| `AWAITING_CONFIRM` | Chờ staff xác nhận (chuyển khoản) |

---

## 6. PaidStatus (Enum)

**File:** `enums/PaidStatus.java`

| Giá trị |
|---|
| `PAID` |
| `UNPAID` |

> Có vẻ trùng với `PaidStatusFilter` (chỉ khác không có `AWAITING_CONFIRM`).

---

## 7. ConfirmationStatus (Enum)

**File:** `enums/ConfirmationStatus.java`

| Giá trị | Mô tả |
|---|---|
| `CONFIRMED` | Staff đã xác nhận |
| `FAILED` | Xác nhận thất bại |

---

## 8. ParkingSlot.slotStatus (String) — Trạng thái chỗ đỗ

**Entity:** `ParkingSlot` (column `slot_status`)
**Set ở:** `ReservationService`, `ParkingSessionService`, `ManagerBuildingSetupService`, `SlotStatusSyncJob`

| Giá trị | Mô tả | Set khi nào |
|---|---|---|
| `AVAILABLE` | Trống, có thể gửi | Tạo slot, sau khi checkout, sau khi reservation bị hủy/hết hạn |
| `OCCUPIED` | Đang có xe (đã check-in) | `ParkingSessionService.checkIn()` |
| `RESERVED` | Đã được reservation giữ chỗ | Khi reservation được APPROVED |
| `PENDING_EXIT` | Đã trả tiền, chờ ra | Sau khi thanh toán (chưa ra cổng) |

**State machine:**
```
AVAILABLE ──reservation APPROVED──► RESERVED
RESERVED ──check-in──► OCCUPIED
OCCUPIED ──checkout──► PENDING_EXIT
PENDING_EXIT ──exit confirm──► AVAILABLE
(any) ──cancel──► AVAILABLE
```

**Background job:** `SlotStatusSyncJob` chạy mỗi 60s — tự động fix `RESERVED` slots không có reservation, `OCCUPIED` slots không có session active.

---

## 9. Reservation.reservationStatus (String) — Vòng đời đặt chỗ

**Entity:** `Reservation` (column `reservation_status`)
**Default:** `PENDING`
**Set ở:** `ReservationService`

| Giá trị | Mô tả |
|---|---|
| `PENDING` | Vừa tạo, chờ duyệt |
| `APPROVED` | Đã duyệt (slot → RESERVED) |
| `REJECTED` | Staff từ chối |
| `CANCELLED` | User hủy / tự động hủy |
| `EXPIRED` | Hết hạn grace period (15 phút) |
| `COMPLETED` | Đã check-in xong |
| `NO_SHOW` | Quá giờ mà không đến |
| `CHECKED_IN` | User đã đến (alias của COMPLETED?) |
| `PENDING_PAYMENT` | Đang chờ trả tiền |

> ⚠️ **`CHECKED_IN`** có vẻ bị thừa — `COMPLETED` đã có nghĩa tương tự.

---

## 10. User.status (String)

**Entity:** `User` (column `status`)
**Default:** `ACTIVE`

| Giá trị | Set khi nào |
|---|---|
| `ACTIVE` | Mặc định khi tạo |
| `INACTIVE` | Bị vô hiệu hóa |
| `BLOCKED` | Bị khóa (vi phạm) |

> Chỉ `UserManagementServiceImpl` (manager) mới set, `DriverServiceImpl` chỉ set ACTIVE.

---

## 11. Vehicle.status (String)

**Entity:** `Vehicle` (column `status`)
**Default:** `ACTIVE`

| Giá trị | Ai được set | Mô tả |
|---|---|---|
| `ACTIVE` | Cả driver & manager | Đang sử dụng |
| `INACTIVE` | Driver hoặc Manager | Tạm ngưng |
| `BLOCKED` | Chỉ Manager | Bị khóa (vd: vi phạm) |
| `DELETED` | DriverServiceImpl.softDelete | Xóa mềm |

> **Lưu ý:** `DELETED` chỉ set 1 chỗ (`DriverServiceImpl.softDelete` dòng 178) nhưng không có trong whitelist `MANAGER_VEHICLE_STATUSES`.

---

## 12. Ticket.status (String)

**Entity:** `Ticket` (column `status`)
**Default:** `ACTIVE`

| Giá trị | Mô tả |
|---|---|
| `ACTIVE` | Vé còn hiệu lực |
| `USED` | Đã sử dụng (sau khi check-in) |

> Guest ticket: `ParkingSessionService.guestCheckIn` set status `ACTIVE`, sau đó `markUsed()` set `USED`.

---

## 13. Building.status (String)

**Entity:** `Building` (column `status`)
**Default:** `ACTIVE`
**Whitelist** (`ManagerBuildingSetupService`): `ACTIVE`, `INACTIVE`, `MAINTENANCE`

---

## 14. Floor.status (String)

**Entity:** `Floor` (column `status`)
**Default:** `ACTIVE`
**Cùng whitelist với Building:** `ACTIVE`, `INACTIVE`, `MAINTENANCE`

---

## 15. Zone.status (String)

**Entity:** `Zone` (column `status`)
**Default:** `ACTIVE`
**Whitelist:** `ACTIVE`, `INACTIVE`, `FULL`, `MAINTENANCE`

| Giá trị | Mô tả |
|---|---|
| `ACTIVE` | Hoạt động bình thường |
| `INACTIVE` | Ngưng |
| `FULL` | Hết chỗ (tự động set khi slot hết AVAILABLE) |
| `MAINTENANCE` | Bảo trì |

---

## 16. PricingPolicy.status (String)

**Entity:** `PricingPolicy` (column `status`)
**Default:** `ACTIVE`

| Giá trị | Mô tả |
|---|---|
| `ACTIVE` | Đang áp dụng |
| `INACTIVE` | Ngưng |

**Logic auto-deactivate:** `PricingPolicyService.updateExpiredPolicies()` chạy mỗi 60s — tự set INACTIVE khi `effectiveTo < NOW()`.

---

## 17. ParkingSession.sessionStatus (String)

**Entity:** `ParkingSession` (column `session_status`)
**Default:** `ACTIVE`

> **Trùng với `SessionStatus` enum** nhưng dùng String. Nên đồng bộ.

| Giá trị thực tế dùng | Mô tả |
|---|---|
| `PENDING_PAYMENT` | Sau khi check-in, chờ thanh toán (default sau checkin) |
| `ACTIVE` | Sau khi payment success webhook chuyển từ PENDING_PAYMENT |
| `COMPLETED` | Đã checkout |

> Cập nhật theo refactor proposal: checkin set `PENDING_PAYMENT`, payment webhook set `ACTIVE`.

---

## 18. ParkingSession.paymentStatus (String)

**Entity:** `ParkingSession` (column `payment_status`)
**Default:** `UNPAID`

| Giá trị | Set ở đâu |
|---|---|
| `UNPAID` | Default, khi check-in, khi checkout nhưng chưa trả |
| `PAID` | Sau khi thanh toán thành công (cash, banking, momo, vnpay, payos) |
| `FAILED` | Thanh toán lỗi |

> **Trùng với `SessionPaymentStatus` enum** nhưng dùng String.

---

## Vấn đề phát hiện

### 🔴 Vấn đề 1: Enum bị khai báo nhưng không dùng
File `enums/SessionStatus`, `PaymentStatus`, `SessionPaymentStatus`, `PaidStatus`, `PaidStatusFilter`, `ConfirmationStatus` được khai báo, nhưng code thực tế dùng **String literals** trong entity/service. Có nguy cơ typo (vd: `PEDDING` thay vì `PENDING`).

### 🟡 Vấn đề 2: Trùng lặp giá trị status
- `PAID` vs `SUCCESS` vs `CONFIRMED` trong PaymentStatus enum
- `CHECKED_IN` vs `COMPLETED` trong Reservation status
- `DELETED` chỉ set ở 1 chỗ, không có trong whitelist

### 🟡 Vấn đề 3: Vehicle status `DELETED` ngoài whitelist
`DriverServiceImpl.softDelete()` set `DELETED` nhưng `MANAGER_VEHICLE_STATUSES` không có giá trị này → có thể gây lỗi khi manager update.

### 🟢 Đề xuất cải tiến
1. **Refactor String status → Enum** trong các entity (đặc biệt `ParkingSession`, `Reservation`, `Vehicle`).
2. **Bỏ enum thừa** không dùng (vd: `PaidStatus`, `ConfirmationStatus`).
3. **Bổ sung `DELETED` vào whitelist** `MANAGER_VEHICLE_STATUSES` hoặc đổi sang `INACTIVE`.
4. **Thống nhất convention:** `PAID` vs `SUCCESS` (chỉ giữ 1).
5. **Thêm DB CHECK constraint** để ngăn typo: `CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED'))`.

---

## Tóm tắt file liên quan

```
src/main/java/fpt/swp391/parkingmanagement/
├── enums/
│   ├── SessionStatus.java
│   ├── SessionPaymentStatus.java
│   ├── PaymentStatus.java
│   ├── PaymentMethod.java
│   ├── PaidStatusFilter.java
│   ├── PaidStatus.java
│   ├── ConfirmationStatus.java
│   └── EnumParser.java
├── entity/
│   ├── User.java (status)
│   ├── Vehicle.java (status)
│   ├── Ticket.java (status)
│   ├── Building.java (status)
│   ├── Floor.java (status)
│   ├── Zone.java (status)
│   ├── ParkingSlot.java (slotStatus)
│   ├── ParkingSession.java (sessionStatus, paymentStatus)
│   ├── Reservation.java (reservationStatus)
│   └── PricingPolicy.java (status)
└── service/
    ├── ReservationService.java
    ├── ParkingSessionService.java
    ├── PaymentService.java
    ├── ManagerBuildingSetupService.java
    ├── ManagerStaffService.java
    ├── UserManagementServiceImpl.java
    ├── DriverServiceImpl.java
    ├── VehicleService.java
    ├── PricingPolicyService.java
    └── DashboardStatsService.java
```
