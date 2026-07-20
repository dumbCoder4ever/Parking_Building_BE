# Add Flows — GIAI ĐOẠN 5→10 (+ Payment / OCR Checkout)
> Nguồn: codebase `Parking_Building_BE` (đọc từ service/controller/entity thật).  
> Mỗi giai đoạn ghi rõ **ĐÃ CÓ / ĐÃ CÓ MỘT PHẦN / CHƯA IMPLEMENT**.

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
| Slot | `AVAILABLE` \| `RESERVED` \| `OCCUPIED` \| `MAINTENANCE` (+ code còn set `PENDING_EXIT`) |

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
8. Jobs: `SlotStatusSyncJob` (30s) orphan RESERVED/OCCUPIED → AVAILABLE; `AutoCancelReservationJob` (60s) hết grace → EXPIRED.

### Flow hiện tại
```
Manager → PATCH Building/Floor/Zone = MAINTENANCE
→ Availability / Reservation mới bị chặn theo filter
→ Session đang gửi vẫn checkout/thanh toán bình thường
→ (Không cascade tự đổi từng Slot)
```

### Chưa có
- API set Slot → `MAINTENANCE`
- Cascade khi Building/Floor vào MAINTENANCE
- Rule API chặn bảo trì khi đang RESERVED/OCCUPIED
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
- Occupancy: total/available/occupied/reserved/pendingExit + theo building; rate = (occ+reserved)/total
- Sessions: ACTIVE, hôm nay, tháng; Driver vs Guest; avg duration/fee
- Reservations: PENDING/APPROVED/COMPLETED/CANCELLED/EXPIRED
- Users, Incidents
- Revenue theo method + trend ngày (mặc định 7 ngày)

### Manager revenue
- Tổng revenue + count payment PAID theo `paymentTime`
- Breakdown theo Building (không có payment vẫn hiện 0)

### Business Rule
1. Doanh thu chỉ payment `PAID`.
2. Occupancy từ `slot_status` hiện tại.
3. Có cache dashboard.

### Chưa có
- Manager chưa có stats đầy đủ như Admin
- Không filter theo building được gán cho Manager
- Không chart Peak Hour

---

## GIAI ĐOẠN 7 — BUILDING RULES
**(CHƯA IMPLEMENT — mới có nền tảng)**

### Hiện có
- `Building.operatingStartTime` / `operatingEndTime` (validate end > start lúc create/update)
- `parkingRules` **hardcode** giống nhau ở `BuildingService.listAvailableBuildings` và `ReservationService.buildBuildingEnrichment`:
  > "Vui lòng đặt trước chỗ đỗ xe. Xuất trình mã vé khi check-in. Giữ vé cẩn thận khi rời khỏi bãi đỗ."

### Chưa có
- Entity/bảng BuildingRule, CRUD per-building
- Enforce rule lúc Reservation / Guest check-in
- Chặn reserve/check-in ngoài giờ hoạt động

### Thiết kế mục tiêu (bám kiến trúc hiện tại)
```
Manager → CRUD Rule theo Building (ACTIVE/INACTIVE)
Driver → xem Rule trước Reservation
Backend → validate khi POST /api/reservations và Guest Auto Assignment
```
Ví dụ rule tương lai: không gửi qua đêm, max time, cấm Truck sau 22h, Visitor.

---

## GIAI ĐOẠN 8 — PEAK HOUR ANALYSIS
**(CHƯA IMPLEMENT)**

### Hiện có liên quan
- `PricingPolicy`: basePrice, hourlyRate, maxHours — **không** peak multiplier
- `PricingService.calculateByPolicy`: flat hourly  
  `hours = max(1, ceil(minutes/60))`  
  `fee = basePrice + hourlyRate*(hours-1)` (cap maxHours)
- Không controller/job/DTO Peak Hour

### Thiết kế mục tiêu
- Manager xem khung giờ đông / occupancy theo giờ (từ `checkinTime`, `reservationStart`)
- Driver Reservation trong Peak → Notification cảnh báo (không chặn nếu còn slot, trừ Building Rule)

---

## GIAI ĐOẠN 9 — AI ANALYTICS
**(CHƯA IMPLEMENT — hiện chỉ heuristic)**

### Hiện có (không phải AI)
Guest auto-assign:
`ParkingSlotRepository.findFirstAvailableByBuildingAndVehicleType`  
→ `ORDER BY floorLevel ASC, slotName ASC` (tầng thấp + tên slot)

### Guest Auto Assignment (đã cập nhật trong Core)
- Guest không chọn Slot
- Backend chọn AVAILABLE + đúng Vehicle Type
- Driver Reservation vẫn tự chọn Slot

### Thiết kế AI mục tiêu
- Không chatbot; AI nghiệp vụ: dự đoán peak/occupancy, gợi ý slot, phát hiện bất thường
- AI chỉ đề xuất — Staff/Backend rule quyết định cuối
- Thiếu data → fallback heuristic tầng thấp hiện tại

---

## GIAI ĐOẠN 10 — AUDIT LOG
**(CHƯA IMPLEMENT)**

### Hiện có
- `log.info` / console log — **không** persist query được
- Không entity `AuditLog`, không API lịch sử

### Thao tác BE nên ghi audit sau này
Check-in/Checkout · Payment initiate/PAID/FAILED · Cancel/Expire Reservation · force-reset Slot · đổi status Building/Floor/Zone · Resolve Incident · Pricing Policy · đổi role/status User

### Thiết kế mục tiêu
Mỗi bản ghi: `actor` (userId|SYSTEM), role, action, entityType+id, before/after, timestamp, buildingId  
Job (`AutoCancel`, `SlotStatusSync`) → actor = SYSTEM  
Manager theo Building; Admin all; không sửa/xóa từ UI nghiệp vụ

---

## Bảng tóm tắt theo code

| Đoạn | Status | Class chính |
|---|---|---|
| 5. Payment | ĐÃ CÓ | `PaymentService`, VNPay, PayOS |
| 6. OCR Checkout | ĐÃ CÓ MỘT PHẦN | `ParkingSessionService.guestCheckoutOcr` |
| GĐ 5 Maintenance | ĐÃ CÓ MỘT PHẦN | `ManagerBuildingSetupService` |
| GĐ 6 Dashboard | ĐÃ CÓ MỘT PHẦN | `DashboardStatsService`, `RevenueDashboardService` |
| GĐ 7 Building Rules | CHƯA | hardcode `parkingRules` |
| GĐ 8 Peak Hour | CHƯA | — |
| GĐ 9 AI Analytics | CHƯA | heuristic `findFirstAvailable...` |
| GĐ 10 Audit Log | CHƯA | — |
