# Project Progress Snapshot - 2026-07-01

> Cập nhật nhanh tiến độ dựa trên cấu trúc file trong `Parking_Building_BE/src/main/java`.

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