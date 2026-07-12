# Codebase Index — Parking Building Management System (BE)

> Indexed: 2026-07-12  
> Module: `Parking_Building_BE` — Spring Boot 3.5 / Java 17 / MySQL  
> Branch snapshot: `huy` (merged `develop`)

---

## 1. Tổng quan

| Hạng mục | Giá trị |
|---|---|
| **Tên ứng dụng** | `parkingmanagement` |
| **Group ID** | `fpt.swp391` |
| **Port mặc định** | `8080` |
| **Database** | MySQL (Railway) |
| **Frontend URL** | `http://localhost:5173` |
| **API docs** | Swagger UI (springdoc-openapi) |
| **Build** | Gradle |

### Tech stack

| Layer | Công nghệ |
|---|---|
| Framework | Spring Boot 3.5.0 |
| Security | Spring Security + JWT (jjwt 0.11.5) |
| ORM | Spring Data JPA / Hibernate |
| Cache | Caffeine (in-memory) |
| Realtime | WebSocket (`/ws`) |
| Payment | VNPay (sandbox), PayOS |
| OCR | Plate Recognizer API (cloud) |
| Media | Cloudinary |
| Email | Jakarta Mail (Gmail SMTP) |
| HTTP client (OCR) | Spring WebFlux |

---

## 2. Cấu trúc package

```
fpt.swp391.parkingmanagement/
├── ParkingmanagementApplication.java   # @EnableScheduling, @EnableCaching
├── config/          (12)   # Security, JWT, CORS, OpenAPI, PayOS, VNPay, WS, PlateRecognizer, Exception, seed
├── controller/      (20)   # REST API endpoints
├── dto/             (92)   # Request/Response objects
├── entity/          (14)   # JPA entities
├── enums/           (8)    # Status / payment enums + EnumParser
├── exception/       (4)    # ErrorCode, BaseAPIException, …
├── job/             (2)    # Scheduled background jobs
├── repository/      (18)   # JpaRepository + projections
├── service/         (30)   # Business logic (+ impl/ for Incident, Driver, UserManagement)
└── util/            (1)    # VnPayUtil
```

**Resources:** `src/main/resources/application.properties`, `src/main/resources/db/*.sql`  
**Tests:** `src/test/java/...` (2 files)

---

## 3. Domain model (Entity)

| Entity | Mô tả | Quan hệ chính |
|---|---|---|
| `User` | Tài khoản (DRIVER, STAFF, MANAGER, ADMIN) | → Vehicle, Reservation, ParkingSession |
| `Vehicle` | Xe đăng ký (biển số, loại, màu) | → User, VehicleType |
| `VehicleType` | Loại xe (Car, Motorbike, VIP…) | → PricingPolicy, Zone |
| `Building` | Tòa nhà bãi xe | → Floor, BuildingStaff |
| `Floor` | Tầng trong building | → Zone |
| `Zone` | Khu vực theo loại xe | → ParkingSlot |
| `ParkingSlot` | Chỗ đỗ (AVAILABLE/RESERVED/OCCUPIED/PENDING_EXIT) | → Zone |
| `Reservation` | Đặt chỗ trước | → User, Vehicle, ParkingSlot, Ticket |
| `Ticket` | Vé/QR code | → Reservation |
| `ParkingSession` | Lượt gửi xe (check-in → checkout) | → User, Vehicle, ParkingSlot, Payment |
| `Payment` | Giao dịch thanh toán | → ParkingSession |
| `PricingPolicy` | Bảng giá theo loại xe | → VehicleType |
| `Incident` | Sự cố (mất vé, sai biển số…) | → ParkingSession |
| `BuildingStaff` | Gán staff vào building | → User, Building |

### Luồng dữ liệu chính

```
User → Vehicle → Reservation → Ticket → ParkingSession → Payment
Manager quản lý: Building → Floor → Zone → ParkingSlot
Guest (không account): GuestSession → ParkingSession → Payment
```

---

## 4. Roles & phân quyền

| Role | Authority | Phạm vi API |
|---|---|---|
| `ROLE_DRIVER` | Người gửi xe | Đặt chỗ, xem slot, quản lý xe cá nhân, thanh toán |
| `ROLE_STAFF` | Nhân viên bãi | Check-in/out, duyệt reservation, xử lý session |
| `ROLE_MANAGER` | Quản lý bãi | CRUD building/floor/zone/slot, pricing, staff, dashboard |
| `ROLE_ADMIN` | Quản trị hệ thống | User management, admin dashboard, migration |

**Security rules** (`SecurityConfig`):
- Public: `/api/auth/**`, `/api/enums`, `/api/public/**`, `/api/ocr/**`, `/ws/**`, Swagger
- Admin only: `/api/admin/**`
- Manager + Admin: `/api/manager/**`
- Còn lại: JWT required

---

## 5. Controllers & API map

### 5.1 Auth (`AuthController` — `/api/auth`)

| Method | Path | Mô tả |
|---|---|---|
| POST | `/register` | Đăng ký (mặc định ROLE_DRIVER) |
| POST | `/login` | Đăng nhập → JWT |
| POST | `/forgot-password` | Gửi OTP qua email |
| POST | `/verify-otp` | Xác minh OTP |
| POST | `/reset-password` | Đặt lại mật khẩu |

### 5.2 User (`UserController` — `/api`)

| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET/PUT | `/users/me` | Auth | Profile (PUT multipart) |
| PUT | `/users/me/password` | Auth | Đổi mật khẩu |
| GET/POST/PUT/DELETE | `/users/me/vehicles` | Auth | CRUD xe cá nhân |
| GET | `/users/me/sessions` | Auth | Lịch sử gửi xe |
| GET | `/users/me/sessions/current` | Auth | Session đang active |
| GET | `/users/me/payments` | Auth | Lịch sử thanh toán |
| GET | `/users/me/stats` | Auth | Thống kê cá nhân |
| CRUD | `/admin/users/**` | ADMIN | Quản lý user / role / status |

### 5.3 Building — Discovery (`BuildingController` — `/api`)

| Method | Path | Mô tả |
|---|---|---|
| GET | `/buildings/available` | Building còn slot (filter type/name/address) |
| GET | `/zones/{zoneId}/slots` | Grid slot + pricing + reservation hint |

### 5.4 Reservation (`ReservationController` — `/api`)

| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET | `/slots/availability` | Auth | Slot trống theo filter |
| POST | `/reservations` | DRIVER | Tạo đặt chỗ |
| GET | `/reservations/me` | DRIVER | Reservation của tôi |
| GET | `/staff/buildings` | STAFF | Building được gán |
| GET | `/staff/reservations/**` | STAFF | Queue, filter, by-code |
| PATCH | `/staff/reservations/{code}/status` | STAFF | Approve/Reject/Cancel |

### 5.5 Parking Session

| Controller | Path | Mô tả |
|---|---|---|
| `ParkingSessionController` | `POST /api/sessions/checkin` | Check-in (multipart) |
| | `POST /api/sessions/checkout` | Checkout + tính phí |
| | `GET /api/sessions/estimate` | Ước tính phí |
| | `PATCH /api/sessions/{id}/confirm-exit` | Xác nhận xe ra cổng |
| `QuickSessionController` | `POST /api/sessions/quick-checkin` | Check-in nhanh (multipart) |

### 5.6 Guest Session (`GuestSessionController` — `/api/sessions/guest`)

| Method | Path | Mô tả |
|---|---|---|
| POST | `/checkin` | Check-in khách vãng lai (multipart) |
| POST | `/checkout` | Checkout khách (multipart) |
| POST | `/checkin/ocr` | Check-in + OCR biển số |
| POST | `/checkout/ocr` | Checkout + OCR biển số |
| GET | `/{sessionId}`, `/plate/{plate}`, `/ticket/{code}` | Tra cứu session |

### 5.7 Payment

| Controller | Path prefix | Mô tả |
|---|---|---|
| `PaymentController` | `/api/payments` | Initiate, confirm, failure, list |
| `VnPayController` | `/api/payments/vnpay` | IPN + return callback |
| `PayOSController` | `/api/payments/payos` | Webhook, return, cancel, confirm-webhook |

**Payment methods:** CASH, BANKING, MOMO, VNPAY, PAYOS

### 5.8 Manager

| Controller | Path prefix | Mô tả |
|---|---|---|
| `ManagerBuildingSetupController` | `/api/manager/setup` | CRUD building/floor/zone/slot; force-reset; occupancy |
| `ManagerStaffController` | `/api/manager` | Gán/bỏ staff ↔ building |
| `ManagerController` | `/api/manager` | Drivers/vehicles, transfer owner, revenue dashboard |
| `PricingPolicyController` | `/api/manager/pricing-policy` | CRUD bảng giá |

### 5.9 Khác

| Controller | Path | Mô tả |
|---|---|---|
| `VehicleController` | `/api/vehicles` | Loại xe + xe cá nhân (`/types`, `/me`) |
| `IncidentController` | `/api/incidents` | CRUD / status / stats sự cố |
| `OcrController` | `/api/ocr` | Plate Recognizer — JSON + upload |
| `AdminDashboardController` | `/api/admin/dashboard` | Stats tổng hệ thống |
| `EnumController` | `/api/enums` | Danh sách enum/status |
| `AdminMigrationController` | `/api/admin/migrate` | DB migration helper |

---

## 6. Services (business logic)

| Service | Trách nhiệm |
|---|---|
| `AuthService` / `JwtService` | Register, login, JWT |
| `OtpService` / `EmailService` | Forgot password flow |
| `UserService` / `UserManagementService` | Profile + admin user CRUD |
| `DriverService` | Driver-facing session/history helpers |
| `ReservationService` | Tạo/duyệt/hủy reservation, slot assignment |
| `ParkingSessionService` | Check-in, checkout, guest/quick flow, fee calc |
| `PricingService` / `PricingPolicyService` | Tính phí, quản lý policy |
| `PaymentService` / `VnPayService` / `PayOSService` | Thanh toán + gateway |
| `BuildingService` | Availability + zone slot grid |
| `ManagerBuildingSetupService` | CRUD cấu trúc bãi xe |
| `ManagerStaffService` | Gán staff ↔ building |
| `ManagerDriverService` | Quản lý driver/vehicle (manager view) |
| `DashboardStatsService` | Admin dashboard metrics |
| `RevenueDashboardService` | Doanh thu manager |
| `IncidentService` | Xử lý sự cố |
| `PlateRecognizerService` | OCR biển số qua Plate Recognizer API |
| `CloudinaryService` | Upload ảnh |
| `NotificationService` | WebSocket notifications |
| `VehicleService` / `VehicleTypeCacheService` / `VehicleTypeSyncService` | Xe + cache loại xe |

---

## 7. Config classes

| Config | Vai trò |
|---|---|
| `SecurityConfig` | JWT filter chain, RBAC |
| `JwtAuthenticationFilter` | Parse Bearer token |
| `CorsConfig` | CORS |
| `OpenApiConfig` | Swagger / springdoc |
| `WebSocketConfig` | `/ws` |
| `VnPayConfig` / `PayOSConfig` | Payment gateways |
| `PlateRecognizerConfig` | OCR cloud client |
| `CloudinaryConfig` | Media upload |
| `GlobalExceptionHandler` | API error mapping |
| `DbSeedRunner` / `VehicleTypeDataInitializer` | Seed / init data |

---

## 8. Background jobs

| Job | Cron | Mô tả |
|---|---|---|
| `AutoCancelReservationJob` | Scheduled | Hủy reservation quá grace period (mặc định 30 phút) |
| `SlotStatusSyncJob` | Mỗi 60s | Đồng bộ slot status với reservation/session thực tế |

---

## 9. Cấu hình quan trọng

| Key | Giá trị / Mô tả |
|---|---|
| `parking.max-car-hours` | 336 (14 ngày) |
| `parking.max-motorbike-hours` | 168 (7 ngày) |
| `parking.grace-period-minutes` | 30 |
| `jwt.expiration` | 86400000 ms (24h) |
| `spring.cache.caffeine.spec` | max 500 entries, TTL 300s |
| `spring.jpa.hibernate.ddl-auto` | `none` (schema qua SQL migrations) |
| `plate-recognizer.api-url` | `https://api.platerecognizer.com/v1/plate-reader/` |
| `plate-recognizer.regions` | `vn` |
| `frontend.url` | `http://localhost:5173` |

---

## 10. Database migrations

### `src/main/resources/db/`

| File | Mục đích |
|---|---|
| `parking_db.sql` | Schema chính + seed |
| `init.sql` | Init cơ bản |
| `auto_cancel_reservation.sql` | Stored procedure auto-cancel |
| `add_indexes_for_availability_query.sql` | Index tối ưu query slot |
| `add_guest_fields_to_parking_sessions.sql` | Guest session columns |
| `add_max_hours_to_reservations.sql` | Max hours trên reservation |
| `seed_pricing_policies.sql` / `simplify_pricing_policies.sql` | Pricing |
| `create_audit_logs_table.sql` | Audit log |
| `fix_*.sql`, `update_*.sql`, `seed_slots_*.sql` | Patches |

### `docs/db/` (ops / Railway)

Railway migration scripts, PayOS/payment alters, Bitexco seed, building staff, floors/slots, test users, v.v.

---

## 11. Tests

| File | Phạm vi |
|---|---|
| `ReservationControllerIntegrationTest` | Integration test reservation flow |
| `ParkingmanagementApplicationTests` | Context load test |

---

## 12. Tài liệu liên quan

| File | Nội dung |
|---|---|
| `docs/requirements-and-main-flows.md` | Yêu cầu & core flows |
| `docs/STATUS_REFERENCE.md` | Tổng hợp status enums/strings |
| `docs/PROGRESS.md` | Snapshot tiến độ |
| `docs/EXISTING_SYSTEMS.md` | Existing systems analysis |
| `docs/payment-setup.md` | Hướng dẫn VNPay/PayOS |
| `docs/PLATERECOGNIZER_OCR_SETUP.md` | Cấu hình Plate Recognizer OCR |
| `docs/RAILWAY_DEPLOY_GUIDE.md` / `docs/setupRailway.md` | Deploy Railway |
| `docs/FORGOT_PASSWORD_API.md` | Forgot password API |
| `docs/swagger_examples.md` | Swagger examples |
| `docs/postman/*.json` | Postman collections |

---

## 13. File count snapshot (2026-07-12)

| Package | `.java` files |
|---|---|
| config | 12 |
| controller | 20 |
| dto | 92 |
| entity | 14 |
| enums | 8 |
| exception | 4 |
| job | 2 |
| repository | 18 |
| service (+ impl) | 30 |
| util | 1 |
| **Total (main)** | **~201** |
