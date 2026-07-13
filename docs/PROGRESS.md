# Project Progress Snapshot

> File này tổng hợp tiến độ dự án. Mục "Today's work" ghi lại những gì đã làm trong phiên làm việc gần nhất.

---

## Today's work — 2026-07-13 (phiên làm việc sáng → chiều)

### 1. Hoàn tất merge `khanh` → `develop`
- Merge commit `53335d1` đã resolve conflict `ParkingSessionRepository.java` (chọn giữ cả 2 method `findActiveSessionsByPlateNumber` của HEAD và `findActiveGuestByPlateNumber` có sẵn).
- Working tree hiện **clean** trên branch `develop`.

### 2. Trạng thái code BE sau merge
| Khu vực | File chính | Đã có | Còn thiếu |
|---|---|---|---|
| Availability screen | `BuildingController.java` (line 42, 62) | `GET /buildings/available`, `GET /zones/{zoneId}/slots` | **`GET /buildings/{id}/floors` chưa có** (FE đang gọi → 404) |
| OCR API | `OcrController.java` | `POST /ocr/plate` (URL), `POST /ocr/plate/upload` (multipart) | OK |
| Check-in chính | `ParkingSessionController.checkin()` (line 52) | Hỗ trợ cả manual `ticketCode+plateNumber` lẫn OCR `plateImage+mode` | Đang có **2 luồng song song** (`checkin` cũ + `quick-checkin`) → rườm rà |
| Quick check-in | `QuickSessionController.quickCheckin()` (line 35) | `@Deprecated`, đã được redirect vào `checkin` qua mode DRIVER/GUEST | Endpoint gốc vẫn tồn tại, FE chưa migrate hết |
| Driver check-out | `ParkingSessionController.driverCheckout()` (line 140) | Tách riêng, validate `paymentStatus=PAID` | OK |
| Guest check-out | `ParkingSessionController.guestCheckoutV2()` (line 162) + `GuestSessionController` cũ | 2 path song song (cũ + v2) | Cần dọn dẹp |
| Repository | `ParkingSessionRepository.findActiveByPlateNumber` (line 115) | Method mới merge vào, **CHƯA được gọi ở bất kỳ đâu** | Cần wire-up vào `quickGuestCheckin` để chống duplicate |

### 3. Lỗi / bug còn tồn tại (theo PROPOSAL)
1. **Missing endpoint**: `GET /api/buildings/{id}/floors` → FE `revervationApi.js` đang gọi → 404.
2. **Duplicate check-in**: cả `quickDriverCheckin` lẫn `quickGuestCheckin` đều **không check** xem biển số đã có session `ACTIVE` chưa → có thể tạo 2 session cùng biển số.
3. **State machine `PENDING_PAYMENT`**: chưa được set ở bất kỳ đâu sau check-in (proposal Mục D).
4. **Two parallel checkout**: `ParkingSessionController.guestCheckoutV2` + `GuestSessionController.guestCheckout` cùng tồn tại.

### 4. Tài liệu liên quan
- `docs/PROPOSAL-availability-and-checkin-checkout-refactor.md` — đề xuất refactor tổng thể.
- `docs/STATUS_REFERENCE.md` — thống kê 18 status enums + 3 vấn đề phát hiện (dead enum, trùng giá trị, `DELETED` ngoài whitelist).
- `docs/payment-setup.md` — thiết lập PayOS/VNPay.

### 5. Phiên build 2026-07-13 (chiều) — implement plan "Unify Checkin + Add Floor Endpoint"

#### Phase 1 — Endpoint `/api/buildings/{id}/floors`
- ✅ Tạo [dto/BuildingFloorsResponse.java](src/main/java/fpt/swp391/parkingmanagement/dto/BuildingFloorsResponse.java) (nested `FloorWithZones` + `ZoneSlotSummary`).
- ✅ Thêm `BuildingService.listFloorsOfBuilding(buildingId)` — 1 query building + 1 query floors (EntityGraph) + 1 aggregate slot counts.
- ✅ Thêm endpoint `GET /api/buildings/{id}/floors` trong `BuildingController` (role: DRIVER/STAFF/MANAGER/ADMIN).

#### Phase 2 — Chuẩn bị checkin thống nhất
- ✅ Sửa `findActiveByPlateNumber` trong repository: filter `sessionStatus IN ('ACTIVE', 'PENDING_PAYMENT')` thay vì chỉ `'ACTIVE'`.
- ✅ Thêm helper `validateNoActiveSessionForPlate(plateNumber)` trong `ParkingSessionService` — ném `PLATE_ALREADY_PARKED`.
- ✅ Refactor [dto/CheckinRequest.java](src/main/java/fpt/swp391/parkingmanagement/dto/CheckinRequest.java): bỏ `mode`, gộp field `plateImage` + `buildingId` + `vehicleTypeId` + optional vehicle/guest info.
- ✅ Tạo [dto/CheckinResponse.java](src/main/java/fpt/swp391/parkingmanagement/dto/CheckinResponse.java) mới (gộp `QuickCheckinResponse`, thêm `sessionStatus` + `paymentStatus`).

#### Phase 3 — Đổi checkin chính sang logic tự quyết
- ✅ Thêm `unifiedCheckin(staffEmail, req)` trong `ParkingSessionService`: OCR → check duplicate → tìm reservation PENDING/APPROVED → DRIVER/Guest tự động.
- ✅ Tách `unifiedDriverCheckin(reservation, plate)` — validate plate khớp, slot RESERVED, ticket unused, set `PENDING_PAYMENT`.
- ✅ Tách `unifiedGuestCheckin(req, plate)` — auto-assign slot, tạo vehicle, sinh ticket `G-xxx`, set `PENDING_PAYMENT`.
- ✅ Refactor `ParkingSessionController.checkin()` xuống còn 1 method đơn giản (xóa branch `ocrMode` + manual path cũ). Xóa luôn import `TicketRepository` không dùng.
- ✅ Xóa `QuickSessionController.java` (deprecated, không còn cần).
- ✅ Xóa `dto/QuickCheckinRequest.java` + `dto/QuickCheckinResponse.java` (dead code).
- ✅ Xóa dead code `quickDriverCheckin`, `quickGuestCheckin`, `applyHierarchyQuick` trong `ParkingSessionService`. Refactor `resolvePlateNumber` nhận `MultipartFile` thay vì `QuickCheckinRequest`.

#### Phase 4 — State machine PENDING_PAYMENT
- ✅ (Đã có sẵn) `PaymentService.confirmPaymentSuccess` set `sessionStatus = ACTIVE` khi gặp `PENDING_PAYMENT` (line 162–165).
- ✅ (Đã có sẵn) `confirmExitAndCheckout` validate session phải ở `ACTIVE` (line 570).

#### Phase 5 — Dọn dẹp
- ⏸ `GuestSessionController` — **chưa xóa** (FE có thể còn gọi 7 endpoint cũ; cần confirm với FE trước khi xóa).
- ⏸ `parkingSessionService.checkout()` legacy — **chưa xóa** (vẫn được gọi bởi endpoint `/api/sessions/checkout` đã `@Deprecated`; chờ FE migrate xong).
- ✅ Lint pass trên tất cả file đã sửa.

#### Test manual cần chạy
1. Driver flow: reservation APPROVED → POST `/api/sessions/checkin` (multipart: `plateImage`, `buildingId`) → response `type=DRIVER`, `sessionStatus=PENDING_PAYMENT` → thanh toán qua webhook → session lên `ACTIVE` → POST `/api/sessions/driver/checkout` → `COMPLETED`.
2. Guest flow: không có reservation → POST `/api/sessions/checkin` (multipart: `plateImage`, `buildingId`, `vehicleTypeId`) → response `type=GUEST` → POST `/api/sessions/guest/checkout/v2` với CASH → `COMPLETED` + Payment `PAID`.
3. Duplicate: checkin cùng biển số 2 lần liên tiếp → lần 2 phải trả 409 `PLATE_ALREADY_PARKED`.
4. Floor endpoint: GET `/api/buildings/{id}/floors` → nhận danh sách floors + zones + counts.

---

## 1. Tổng quan package

| Package | Số file | Ghi chú |
|---|---|---|
| `entity/` | 14 | User, Vehicle, Reservation, Ticket, Payment, ParkingSlot, ParkingSession, Incident, Building, Floor, Zone, VehicleType, BuildingStaff, PricingPolicy |
| `repository/` | 14 | Đầy đủ JpaRepository cho mọi entity + projection cho dashboard |
| `dto/` | ~75 | Request/Response riêng cho từng luồng |
| `controller/` | 15 | Auth, User, Vehicle, Reservation, ParkingSession, Payment, PayOS, VNPay, Manager*, Staff, Guest, Enum, AdminDashboard, PricingPolicy |
| `service/` | ~28 interface | Bao gồm AI/Manager/Driver/Staff/Payment/Pricing/WebSocket |
| `service/impl/` | 2 | Chỉ 2 file impl tường minh (`UserManagementServiceImpl`, `DriverServiceImpl`) - các service còn lại nhiều khả năng là class trực tiếp hoặc Spring tự tạo proxy |
| `config/` | 11 | Security, JwtFilter, CORS, OpenAPI, Cloudinary, PayOS, VNPay, WebSocket, DbSeedRunner, VehicleTypeDataInitializer, GlobalExceptionHandler |
| `job/` | ≥2 | `AutoCancelReservationJob`, `SlotStatusSyncJob` |

## 2. Tính năng theo core flow

| Flow | Trạng thái | Bằng chứng |
|---|---|---|
| FLOW 1 - Đặt trước slot | ✅ Có | `ReservationController`, `ReservationService`, `ReservationRepository` |
| FLOW 2 - Staff check-in | ✅ Có | `ParkingSessionController`, `ParkingSessionService` |
| FLOW 3 - Auto cancel reservation | ✅ Có | `@EnableScheduling` + `AutoCancelReservationJob` (cron-based) |
| FLOW 4 - Checkout + thanh toán | ✅ Có | `PaymentController`, `PayOSController`, `VnPayController`, `ParkingSessionController` (checkout) |
| FLOW 5 - Manager dashboard | ✅ Có | `ManagerController`, `ManagerBuildingSetupController`, `RevenueDashboardService`, `DashboardStatsService` |
| FLOW 6 - Incident | ✅ Có | `IncidentRepository` (entity + repo, cần kiểm tra controller/service) |
| FLOW 7 - AI smart slot | 🟡 Có research | Thấy `DriverService`, `VehicleTypeSyncService` - cần đọc chi tiết để xác nhận |
| Realtime (WebSocket) | ✅ Có | `WebSocketConfig`, `WsMessage` DTO, `NotificationService` |

## 3. Tích hợp ngoài

- **Auth**: JWT + Spring Security + JwtAuthenticationFilter.
- **Payment**: PayOS (`payos-java:2.0.1`), VNPay sandbox.
- **Storage**: Cloudinary.
- **Realtime**: WebSocket path `/ws`.

## 4. Cấu hình DB hiện tại

- Railway MySQL public proxy: `thomas.proxy.rlwy.net:26455`.
- Hibernate: `ddl-auto=none`, validate skipped → schema quản lý thủ công qua SQL migrations (`db/*.sql`).
- `show-sql=true` → log SQL (có thể gây chậm console + I/O).

## 5. Việc còn thiếu / cần làm tiếp

1. **Incident**: chưa thấy `IncidentController` / `IncidentService` rõ ràng trong danh sách → có thể chưa hoàn thiện flow.
2. **AI smart slot**: cần đọc chi tiết `DriverService`/`VehicleTypeSyncService` để xác nhận mức độ hoàn thiện.
3. **Service impl**: chỉ 2 file `*ServiceImpl` → nhiều service có thể là class trực tiếp (không theo pattern interface+impl) - nên rà lại.
4. **Test**: có `ReservationControllerIntegrationTest` + `ParkingmanagementApplicationTests` - cần bổ sung test cho các flow khác.
5. **Hiệu năng Railway DB**: cần bật connection pool tuning + có thể cache read-only (xem chi tiết ở phần tư vấn).