# Add Flows — Tổng hợp (Giai đoạn 5→13 + Payment / OCR Checkout)

> Nguồn: codebase `Parking_Building_BE` — đối chiếu service/controller/entity thật.  
> Mỗi giai đoạn ghi rõ **ĐÃ CÓ / ĐÃ CÓ MỘT PHẦN / CHƯA IMPLEMENT**.  
> Cập nhật: 24/07/2026

---

## Mục lục

1. [5. Payment](#5-payment)
2. [6. OCR Checkout](#6-ocr-checkout)
3. [GIAI ĐOẠN 5 — MAINTENANCE](#giai-đoạn-5--maintenance)
4. [GIAI ĐOẠN 6 — MANAGER DASHBOARD](#giai-đoạn-6--manager-dashboard)
5. [GIAI ĐOẠN 7 — BUILDING RULES](#giai-đoạn-7--building-rules)
6. [GIAI ĐOẠN 8 — PEAK HOUR ANALYSIS](#giai-đoạn-8--peak-hour-analysis)
7. [GIAI ĐOẠN 9 — AI ANALYTICS](#giai-đoạn-9--ai-analytics)
8. [GIAI ĐOẠN 10 — AUDIT LOG](#giai-đoạn-10--audit-log)
9. [GIAI ĐOẠN 11 — SCHEDULER](#giai-đoạn-11--scheduler)
10. [GIAI ĐOẠN 12 — SYSTEM CONFIGURATION](#giai-đoạn-12--system-configuration)
11. [GIAI ĐOẠN 13 — REPORT EXPORT](#giai-đoạn-13--report-export)
12. [Bảng tóm tắt](#bảng-tóm-tắt-theo-code)

---

## 5. Payment

**(Theo `PaymentService`, `VnPayController`, `PayOSController` — ĐÃ CÓ)**

### Payment Method
- `CASH`
- `VNPAY`
- `PAYOS`
- `BANKING` / `MOMO` — có trong enum, **chưa có gateway riêng**

### Payment Status
- `PENDING` → `PAID` | `FAILED`

### Session liên quan

| Thời điểm | sessionStatus | paymentStatus |
|---|---|---|
| Sau check-in | `PENDING_PAYMENT` | `UNPAID` |
| Thanh toán OK | `ACTIVE` (nếu đang PENDING_PAYMENT) | `PAID` |
| Sau checkout | `COMPLETED` | `PAID` |

### Luồng CASH
```
Parking Session
→ Staff Checkout (POST /api/sessions/checkout)
→ Tạo Payment PAID ngay (method CASH)
→ Session COMPLETED
→ Slot = AVAILABLE
```

### Luồng điện tử (VNPAY / PAYOS)
```
Session (PENDING_PAYMENT | ACTIVE)
→ POST /api/payments/initiate
→ Payment = PENDING + paymentUrl
→ Driver thanh toán gateway
→ IPN/Webhook (hoặc confirm-success)
→ Payment = PAID; session.paymentStatus = PAID
→ Staff/Driver checkout
   POST /api/sessions/checkout
   hoặc POST /api/sessions/driver/checkout
→ Bắt buộc đã PAID
→ Session COMPLETED
→ Slot = AVAILABLE (hoặc PENDING_EXIT nếu confirm-exit)
```

### Business Rule (code)
1. Initiate chỉ khi session `ACTIVE` hoặc `PENDING_PAYMENT`.
2. CASH không qua `/initiate` — gắn lúc checkout.
3. Checkout VNPAY/PAYOS/MOMO bắt buộc session đã `PAID`.
4. Dashboard doanh thu chỉ tính payment `PAID`.
5. Initiate theo `sessionId` hoặc `ticketCode`.

### API
- `POST /api/payments/initiate`
- `POST /api/payments/confirm-success`
- `POST /api/payments/handle-failure`
- `GET /api/payments`, `/driver/{id}`, `/{paymentId}`
- `GET /api/payments/vnpay/ipn`, `/return`
- `POST /api/payments/payos/webhook` ; `GET .../return`, `/cancel`

---

## 6. OCR Checkout

**(Theo `ParkingSessionService` + `PlateRecognizerService` — ĐÃ CÓ MỘT PHẦN)**

### Luồng Guest OCR Checkout (đã có)
```
Staff → POST /api/sessions/checkout (ticketCode + plateImage)
→ OCR Plate Recognizer
→ Tìm session theo ticket
→ Session phải Guest (không có Reservation)
→ So khớp biển OCR vs biển session
→ Khác → PLATE_MISMATCH
→ Khớp → tính phí
   hours = max(1, ceil(minutes/60))
   fee = basePrice + hourlyRate*(hours-1) (cap maxHours)
→ CASH: Payment PAID ngay | Điện tử: yêu cầu đã PAID
→ Checkout → Slot = AVAILABLE
```

### Luồng Driver Checkout (đã có, chưa OCR)
```
Ticket / sessionId
→ Thanh toán (CASH hoặc đã PAID điện tử)
→ POST /api/sessions/checkout
  hoặc POST /api/sessions/driver/checkout
  hoặc PATCH /api/sessions/{id}/confirm-exit
→ Reservation = COMPLETED
→ Slot = AVAILABLE | PENDING_EXIT
```

### OCR Check-in (đối chiếu — đã có)
```
POST /api/sessions/checkin (plateImage + buildingId)
→ Có Reservation PENDING/APPROVED → Driver check-in
→ Không có → Guest Auto Assignment
→ Confidence < 0.3 → Reject
→ Biển số normalize (bỏ -, space, .) trước khi so khớp
```

### Business Rule
1. Checkout kèm `plateImage` hiện đi vào `guestCheckoutOcr`.
2. Session có Reservation → không dùng nhánh Guest OCR checkout.
3. Estimate: `GET /api/sessions/estimate?ticketCode=`
4. OCR độc lập: `POST /api/ocr/plate`, `/plate/upload`

### Chưa có
- OCR checkout riêng cho Driver (có reservation)
- `confirm-exit` / `driver-checkout` chưa gắn OCR

---

## GIAI ĐOẠN 5 — MAINTENANCE

**(ĐÃ CÓ MỘT PHẦN — `ManagerBuildingSetupService` / `ReservationService`)**

### Phạm vi status trong code

| Level | Status |
|---|---|
| Building / Floor | `ACTIVE` \| `INACTIVE` \| `MAINTENANCE` |
| Zone | `ACTIVE` \| `INACTIVE` \| `FULL` \| `MAINTENANCE` |
| Slot | `AVAILABLE` \| `RESERVED` \| `OCCUPIED` \| `MAINTENANCE` (+ `PENDING_EXIT`) |

### API đã có
- `PATCH /api/manager/setup/buildings/{id}/status`
- `PATCH /api/manager/setup/floors/{id}/status`
- `PATCH /api/manager/setup/zones/{id}/status`
- `GET /api/manager/setup/slots/{id}/occupancy`
- `POST /api/manager/setup/slots/{id}/force-reset`

### Business Rule (theo code thật)
1. Chỉ `MANAGER` / `ADMIN`.
2. Building không `ACTIVE` → không hiện trong danh sách đặt chỗ (`findActiveBuildings`).
3. Floor ≠ `ACTIVE` (gồm MAINTENANCE) → slot không vào availability.
4. Zone `MAINTENANCE` → loại khỏi availability (chỉ lấy `ACTIVE` \| `FULL`).
5. Reservation chỉ khi Slot = `AVAILABLE`.
6. Occupancy detail reject nếu slot `AVAILABLE` hoặc `MAINTENANCE`.
7. `force-reset`: mọi status ≠ AVAILABLE → `AVAILABLE` (không check session/reservation còn sống).
8. Jobs: `SlotStatusSyncJob` (30s); `AutoCancelReservationJob` (60s).
9. **Cascade khi set `MAINTENANCE`:** Building → Floor + Zone + Slot(`AVAILABLE`); Floor → Zone + Slot; Zone → Slot. Slot `RESERVED`/`OCCUPIED`/`PENDING_EXIT` giữ nguyên.
10. Set lại `ACTIVE`/`INACTIVE` **không** cascade ngược.
11. **Auto Zone FULL:** hết slot `AVAILABLE` → zone `FULL`; có slot trống lại → `ACTIVE`.

### Flow hiện tại
```
Manager → PATCH Building/Floor/Zone = MAINTENANCE
→ Cascade xuống các cấp con
→ Availability / Reservation mới bị chặn
→ Session đang chạy vẫn checkout/thanh toán bình thường
```

### Chưa có
- Block check-in khi vừa set MAINTENANCE

---

## GIAI ĐOẠN 6 — MANAGER DASHBOARD

**(ĐÃ CÓ MỘT PHẦN — Admin stats đầy đủ hơn Manager)**

### API đã có

| Role | Endpoint | Service |
|---|---|---|
| ADMIN | `GET /api/admin/dashboard/stats?fromDay&toDay` | `DashboardStatsService` |
| MANAGER | `GET /api/manager/dashboard/revenue?from&to` | `RevenueDashboardService` |

Ops kèm: `/api/manager/drivers`, `/vehicles`, `/setup/**`, `/pricing-policy/**`, `/api/incidents`

### Admin stats trả về
- Occupancy: total/available/occupied/reserved/pendingExit + theo building
- Sessions: ACTIVE, hôm nay, tháng; Driver vs Guest; avg duration/fee
- Reservations: PENDING/APPROVED/COMPLETED/CANCELLED/EXPIRED
- Users, Incidents
- Revenue theo method + trend ngày (mặc định 7 ngày)

### Manager revenue
- Tổng revenue + count payment PAID theo `paymentTime`
- Breakdown theo Building

### Chưa có
- Manager chưa có stats đầy đủ như Admin
- Không filter theo building được gán cho Manager

---

## GIAI ĐOẠN 7 — BUILDING RULES

**(CHƯA IMPLEMENT — mới có nền tảng)**

### Hiện có
- `Building.operatingStartTime` / `operatingEndTime`
- `parkingRules` **hardcode** ở `BuildingService` và `ReservationService`

### Chưa có
- Entity/bảng BuildingRule, CRUD per-building
- Enforce rule lúc Reservation / Guest check-in
- Chặn reserve/check-in ngoài giờ hoạt động

---

## GIAI ĐOẠN 8 — PEAK HOUR ANALYSIS

**(ĐÃ CÓ MỘT PHẦN — `PeakHourService`, `PeakHourNotificationJob`)**

### Hiện có
- `PeakHourService` — phân tích occupancy theo giờ từ session/reservation
- `PeakHourNotificationJob` (10 phút) — cảnh báo qua WebSocket
- DTO: `PeakHourAnalysisResponse`, `PeakHourBucketResponse`
- Config threshold: `PEAK_HOUR_STDDEV_FACTOR` (GĐ 12)

### Chưa có / một phần
- Peak multiplier trong `PricingService` (phí vẫn flat hourly)
- Dashboard chart Peak Hour cho Manager (endpoint riêng có thể chưa đầy đủ)

---

## GIAI ĐOẠN 9 — AI ANALYTICS

**(CHƯA IMPLEMENT — hiện chỉ heuristic)**

### Hiện có (không phải AI)
Guest auto-assign: `ParkingSlotRepository.findFirstAvailableByBuildingAndVehicleType`  
→ `ORDER BY floorLevel ASC, slotName ASC`

### Thiết kế mục tiêu
- Dự đoán peak/occupancy, gợi ý slot thông minh, phát hiện bất thường
- AI chỉ đề xuất — Staff/Backend rule quyết định cuối
- Fallback heuristic tầng thấp hiện tại

---

## GIAI ĐOẠN 10 — AUDIT LOG

**(CHƯA IMPLEMENT — persist query được)**

### Hiện có
- `log.info` / console log
- SQL script `create_audit_logs_table.sql` (chưa có entity Java)

### Thiết kế mục tiêu
Mỗi bản ghi: actor, role, action, entityType+id, before/after, timestamp, buildingId  
Job → actor = SYSTEM

---

## GIAI ĐOẠN 11 — SCHEDULER

**(ĐÃ CÓ)**

| Job | Class | Interval | Ghi chú |
|---|---|---|---|
| Reservation hết hạn | `AutoCancelReservationJob` | 60s | PENDING quá hạn → EXPIRED |
| Giải phóng Slot | `SlotStatusSyncJob` | 30s | Orphan RESERVED/OCCUPIED → AVAILABLE |
| Vehicle Overstay | `VehicleOverstayJob` | 5m | Notify staff/driver |
| Payment Reminder | `PaymentReminderJob` | 5m | Session `PENDING_PAYMENT` |
| Peak Hour Notification | `PeakHourNotificationJob` | 10m | 1 lần / building / giờ |
| Maintenance Notification | `MaintenanceNotificationJob` | 1h | Reminder hạng mục MAINTENANCE |

Events WS: `VEHICLE_OVERSTAY`, `PAYMENT_REMINDER`, `PEAK_HOUR_ALERT`, `MAINTENANCE_REMINDER`

---

## GIAI ĐOẠN 12 — SYSTEM CONFIGURATION

**(ĐÃ CÓ)**

- Entity: `SystemConfig` — bảng `system_configs`
- SQL: `src/main/resources/db/create_system_configs_table.sql`
- Service: `SystemConfigService`
- API (`SystemConfigController`):
  - `GET /api/manager/system-configs`
  - `GET /api/manager/system-configs/{configKey}`
  - `PUT /api/manager/system-configs/{configKey}`

| Key | Default | Dùng bởi |
|---|---|---|
| `GRACE_PERIOD_MINUTES` | 15 | Tạo Reservation |
| `PAYMENT_REMINDER_MINUTES` | 15 | PaymentReminderJob |
| `MAX_PARKING_HOURS` | 24 | VehicleOverstayJob |
| `PEAK_HOUR_STDDEV_FACTOR` | 1.0 | PeakHourService threshold |
| `OVERSTAY_NOTIFY_ENABLED` | true | Bật/tắt overstay job |

---

## GIAI ĐOẠN 13 — REPORT EXPORT

**(ĐÃ CÓ — Driver Report placeholder)**

- Controller: `ReportExportController`
- Service: `ReportExportService`
- `GET /api/manager/reports/export?reportType=&format=&buildingId=&fromDay=&toDay=`
- `reportType`: `REVENUE` \| `INCIDENT` \| `OCCUPANCY` \| `PEAK_HOUR` \| `DRIVER_REPORT`
- `format`: `EXCEL` \| `PDF`
- `DRIVER_REPORT`: entity chưa có → file ghi chú placeholder

---

## Bảng tóm tắt theo code

| Đoạn | Status | Class / API chính |
|---|---|---|
| 5. Payment | ĐÃ CÓ | `PaymentService`, VNPay, PayOS |
| 6. OCR Checkout | ĐÃ CÓ MỘT PHẦN | `ParkingSessionService.guestCheckoutOcr` |
| GĐ 5 Maintenance | ĐÃ CÓ MỘT PHẦN | `ManagerBuildingSetupService` |
| GĐ 6 Dashboard | ĐÃ CÓ MỘT PHẦN | `DashboardStatsService`, `RevenueDashboardService` |
| GĐ 7 Building Rules | CHƯA | hardcode `parkingRules` |
| GĐ 8 Peak Hour | ĐÃ CÓ MỘT PHẦN | `PeakHourService`, `PeakHourNotificationJob` |
| GĐ 9 AI Analytics | CHƯA | heuristic `findFirstAvailable...` |
| GĐ 10 Audit Log | CHƯA | SQL script only |
| GĐ 11 Scheduler | ĐÃ CÓ | 6 scheduled jobs |
| GĐ 12 System Config | ĐÃ CÓ | `SystemConfigService` |
| GĐ 13 Report Export | ĐÃ CÓ MỘT PHẦN | `ReportExportService` |

---

*Tài liệu tổng hợp từ `docs/ADD_FLOWS_GIAI_DOAN_5_10.md` và `docs/ADD_FLOWS_GIAI_DOAN_11_13.md`, cập nhật theo codebase Parking_Building_BE.*
