# 2. Existing Systems

The following systems were analyzed as references for feature design and user experience patterns of the **Parking Building Management System** (FPT SWP391).

---

## 2.1 Parking Building Management System — Backend (FPT SWP391)

**Link:** Repository `Parking_Building_BE` (Spring Boot REST API, port `8080`)

Parking Building Management System is a web-based parking management platform designed for multi-floor buildings. The backend provides REST APIs for slot reservation, parking session lifecycle (check-in/checkout), multi-role access control, dynamic pricing, online payment integration, license plate OCR, and real-time notifications via WebSocket. It targets Vietnamese parking operations with VNPay/PayOS payment gateways and Plate Recognizer cloud OCR.

**Actors:** Driver (ROLE_DRIVER), Parking Staff (ROLE_STAFF), Parking Manager (ROLE_MANAGER), System Admin (ROLE_ADMIN), Guest (walk-in, no account).

**Key Features:** JWT authentication with role-based access; building/floor/zone/slot hierarchy management; slot availability query and reservation with ticket/QR generation; staff check-in/checkout with photo upload (Cloudinary); guest walk-in sessions; quick check-in; dynamic pricing policies per vehicle type; payment via CASH, BANKING, VNPay, PayOS; auto-cancel expired reservations (grace period); background slot status sync; incident reporting; admin/manager dashboards (revenue, occupancy, user stats); license plate OCR (Plate Recognizer API); forgot-password via email OTP; WebSocket notifications; Caffeine caching for read-heavy data.

**Pros:** Complete end-to-end parking flow (reserve → check-in → pay → exit); multi-role RBAC aligned with real parking operations; dual payment gateway support (VNPay + PayOS) suitable for Vietnam; guest mode without registration; cloud OCR reduces manual plate entry for staff; scheduled jobs handle reservation expiry and slot drift; well-documented status machines and API (Swagger, Postman collections); modular Spring Boot architecture with clear controller/service/repository layers.

**Cons:** Status values mix Java enums and raw Strings (risk of inconsistency/typos); AI smart slot allocation marked as optional/research — not fully implemented; limited automated test coverage (mainly reservation integration test); remote Railway MySQL adds latency without full read-replica strategy; OCR depends on Plate Recognizer quota/API and image quality; some payment/enum statuses overlap (PAID vs CONFIRMED vs SUCCESS); frontend is separate repo — BE assumes `localhost:5173` CORS; secrets in `application.properties` should be externalized for production.

**Reference for this system:** Building → Floor → Zone → Slot hierarchy model; reservation queue and staff approval workflow; parking session state machine (ACTIVE → checkout → PENDING_EXIT → COMPLETED); slot status transitions (AVAILABLE ↔ RESERVED ↔ OCCUPIED ↔ PENDING_EXIT); pricing policy per vehicle type with peak/overtime rules; payment initiation + gateway callback (IPN/webhook) pattern; role-separated API namespaces (`/api/manager/**`, `/api/staff/**`, `/api/admin/**`); guest session flow as alternative to registered driver flow.

---

## 2.2 Kiến trúc hiện tại (tóm tắt từ codebase)

```
┌─────────────┐     REST/WS      ┌──────────────────────────────────┐
│  Frontend   │ ◄──────────────► │  Parking_Building_BE (Spring Boot)│
│  (Vite/React│                  │  - 20 Controllers                 │
│   :5173)    │                  │  - 30 Services                    │
└─────────────┘                  │  - 14 Entities / 18 Repositories  │
                                 └──────────┬───────────────────────┘
                                            │
              ┌─────────────────────────────┼─────────────────────────────┐
              ▼                             ▼                             ▼
      ┌──────────────┐            ┌──────────────┐            ┌──────────────┐
      │ MySQL        │            │ VNPay / PayOS│            │ Cloudinary   │
      │ (Railway)    │            │ (Payment)    │            │ (Images)     │
      └──────────────┘            └──────────────┘            └──────────────┘
                                            │
                                    ┌──────────────────┐
                                    │ Plate Recognizer │
                                    │ (Cloud OCR)      │
                                    └──────────────────┘
```

### Core flows đã triển khai

| # | Flow | Trạng thái | Module chính |
|---|---|---|---|
| 1 | User đặt trước slot | ✅ Done | `ReservationController`, `ReservationService` |
| 2 | Staff xác nhận xe vào bãi | ✅ Done | `ParkingSessionController`, `ParkingSessionService` |
| 3 | Auto-cancel reservation | ✅ Done | `AutoCancelReservationJob` |
| 4 | User lấy xe + thanh toán | ✅ Done | `PaymentController`, `VnPayController`, `PayOSController` |
| 5 | Manager quản lý bãi xe | ✅ Done | `ManagerBuildingSetupController`, `ManagerController` |
| 6 | Incident handling | ✅ Done | `IncidentController`, `IncidentService` |
| 7 | Guest walk-in | ✅ Done | `GuestSessionController` |
| 8 | OCR biển số | ✅ Done | `OcrController`, `PlateRecognizerService` |
| 9 | AI smart slot | 🟡 Research | Chưa hoàn thiện |

### Ma trận Actor × Chức năng

| Chức năng | Driver | Staff | Manager | Admin | Guest |
|---|:---:|:---:|:---:|:---:|:---:|
| Đăng ký / đăng nhập | ✅ | — | — | — | — |
| Xem slot trống | ✅ | ✅ | ✅ | — | — |
| Đặt chỗ trước | ✅ | — | — | — | — |
| Duyệt reservation | — | ✅ | — | — | — |
| Check-in / Check-out | — | ✅ | — | — | ✅ |
| Thanh toán online | ✅ | ✅ | — | — | ✅ |
| Quản lý building/slot | — | — | ✅ | — | — |
| Quản lý pricing | — | — | ✅ | — | — |
| Gán staff vào building | — | — | ✅ | — | — |
| Quản lý user / role | — | — | — | ✅ | — |
| Dashboard thống kê | — | — | ✅ | ✅ | — |
| Báo cáo sự cố | ✅ | ✅ | — | — | — |
| OCR biển số | — | ✅ | — | — | — |

---

> Chi tiết đầy đủ API, entity, service: xem [`CODEBASE_INDEX.md`](./CODEBASE_INDEX.md)
