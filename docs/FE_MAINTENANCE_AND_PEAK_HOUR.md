# FE Guide — Maintenance & Peak Hour

Tài liệu dành cho Frontend tích hợp **Giai đoạn 5 (Maintenance)** và **Giai đoạn 8 (Peak Hour Analysis)**.

**Auth:** Bearer JWT — role `MANAGER` hoặc `ADMIN`  
**Base wrapper response:**

```json
{
  "success": true,
  "message": "...",
  "data": { }
}
```

---

## 1. Maintenance (GĐ 5)

Set trạng thái bảo trì theo 4 cấp. Controller: `ManagerBuildingSetupController`.

### 1.1 Status hợp lệ theo cấp

| Level | Status cho phép |
|---|---|
| Building / Floor | `ACTIVE` \| `INACTIVE` \| `MAINTENANCE` |
| Zone | `ACTIVE` \| `INACTIVE` \| `FULL` \| `MAINTENANCE` |
| Slot | `AVAILABLE` \| `MAINTENANCE` *(chỉ 2 giá trị này khi PATCH status)* |

Status slot vận hành khác (`RESERVED`, `OCCUPIED`, `PENDING_EXIT`) do hệ thống/session set — FE **không** PATCH sang các status đó.

### 1.2 API set status

| Level | Method | Endpoint |
|---|---|---|
| Building | `PATCH` | `/api/manager/setup/buildings/{buildingId}/status` |
| Floor | `PATCH` | `/api/manager/setup/floors/{floorId}/status` |
| Zone | `PATCH` | `/api/manager/setup/zones/{zoneId}/status` |
| Slot | `PATCH` | `/api/manager/setup/slots/{slotId}/status` |

**Request body (chung):**

```json
{
  "status": "MAINTENANCE"
}
```

**Response `data`:** object `ManagerSetupResponse` (id, name, type, status, …).

### 1.3 Business rules (FE cần biết)

1. **Slot đang `RESERVED` / `OCCUPIED` / `PENDING_EXIT` → không được set `MAINTENANCE`.**  
   Error message dạng: `Cannot set MAINTENANCE while slot is RESERVED`
2. Muốn đưa slot stuck (`OCCUPIED`/`RESERVED`/`PENDING_EXIT`) về trống: dùng force-reset, **không** PATCH `AVAILABLE` trực tiếp.
3. **Cascade MAINTENANCE (hierarchy):**
   - Building → `MAINTENANCE` ⇒ tất cả Floor + Zone bên dưới cũng → `MAINTENANCE`; slot `AVAILABLE` → `MAINTENANCE`
   - Floor → `MAINTENANCE` ⇒ tất cả Zone bên dưới → `MAINTENANCE`; slot `AVAILABLE` → `MAINTENANCE`
   - Zone → `MAINTENANCE` ⇒ slot `AVAILABLE` trong zone → `MAINTENANCE`
   - Slot đang `RESERVED` / `OCCUPIED` / `PENDING_EXIT` **không** bị cascade (session vẫn checkout bình thường)
4. **Auto Zone FULL:** khi zone không còn slot `AVAILABLE` → status zone tự thành `FULL`; khi có slot trống lại → tự về `ACTIVE`. Không đụng zone đang `MAINTENANCE` / `INACTIVE`.
5. Bỏ bảo trì (set lại `ACTIVE`/`INACTIVE`) **không** cascade ngược — FE/manager tự bật lại từng cấp nếu cần.
6. Building / Floor / Zone set `MAINTENANCE` → không còn trong availability / reservation mới.

### 1.4 API hỗ trợ liên quan

| Mục đích | Method | Endpoint |
|---|---|---|
| Chi tiết slot đang có xe / reserved | `GET` | `/api/manager/setup/slots/{slotId}/occupancy` |
| Force-reset slot về `AVAILABLE` | `POST` | `/api/manager/setup/slots/{slotId}/force-reset` |
| List slots trong zone | `GET` | `/api/manager/setup/zones/{zoneId}/slots` |

**Gợi ý UI Maintenance:**

- Building / Floor / Zone: dropdown status → gọi PATCH tương ứng.
- Slot: chỉ cho chọn `AVAILABLE` / `MAINTENANCE`. Nếu status hiện tại là `RESERVED`/`OCCUPIED`/`PENDING_EXIT` → disable nút “Bảo trì”, hiện lý do.
- Sau khi PATCH thành công → refresh list / cây setup.

### 1.5 Ví dụ

```http
PATCH /api/manager/setup/slots/{slotId}/status
Authorization: Bearer <token>
Content-Type: application/json

{ "status": "MAINTENANCE" }
```

```http
PATCH /api/manager/setup/buildings/{buildingId}/status
Authorization: Bearer <token>
Content-Type: application/json

{ "status": "MAINTENANCE" }
```

---

## 2. Peak Hour Analysis (GĐ 8)

Manager xem khung giờ đông và tỷ lệ lấp đầy. Controller: `ManagerController`.

### 2.1 Khung giờ đông nhất

`GET /api/manager/dashboard/peak-hours`

| Query | Bắt buộc | Mô tả |
|---|---|---|
| `buildingId` | Không | Lọc theo building; bỏ trống = toàn hệ thống |
| `fromDay` | Không | `YYYY-MM-DD` — mặc định = hôm nay − 6 ngày |
| `toDay` | Không | `YYYY-MM-DD` — mặc định = hôm nay |

**Response `data`:**

```json
{
  "buildingId": "uuid-or-null",
  "fromDay": "2026-07-15",
  "toDay": "2026-07-21",
  "averagePerHour": 12.5,
  "peakThreshold": 20.3,
  "peakHours": [8, 9, 17],
  "buckets": [
    { "hour": 0, "sessionCount": 2, "peak": false },
    { "hour": 8, "sessionCount": 45, "peak": true }
  ]
}
```

| Field | Ý nghĩa cho FE |
|---|---|
| `peakHours` | Danh sách giờ (0–23) được đánh dấu peak — dùng label “Khung giờ đông nhất” |
| `buckets` | 24 phần tử — vẽ chart cột theo giờ |
| `buckets[].peak` | `true` → highlight giờ cao điểm trên chart |
| `peakThreshold` | Ngưỡng tính peak (tham khảo) |

Dữ liệu dựa trên số lượng **check-in** theo giờ trong khoảng ngày.

### 2.2 Tỷ lệ lấp đầy (Occupancy rate)

`GET /api/manager/dashboard/stats`

| Query | Bắt buộc | Mô tả |
|---|---|---|
| `buildingId` | Không | Lọc occupancy theo building |
| `fromDay` | Không | Lọc phần stats theo ngày (`YYYY-MM-DD`) |
| `toDay` | Không | Lọc phần stats theo ngày (`YYYY-MM-DD`) |

**Field cần lấy cho “Tỷ lệ lấp đầy”:** `data.occupancy`

```json
{
  "occupancy": {
    "totalSlots": 200,
    "availableSlots": 80,
    "occupiedSlots": 90,
    "reservedSlots": 20,
    "pendingExitSlots": 10,
    "occupancyRate": 55.0,
    "buildings": [
      {
        "buildingId": "...",
        "buildingName": "Building A",
        "totalSlots": 100,
        "availableSlots": 40,
        "occupiedSlots": 45,
        "reservedSlots": 10,
        "pendingExitSlots": 5,
        "occupancyRate": 55.0
      }
    ]
  }
}
```

**Công thức:**  
`occupancyRate = (occupiedSlots + reservedSlots) / totalSlots * 100`

| Field FE dùng | UI gợi ý |
|---|---|
| `occupancy.occupancyRate` | % lấp đầy tổng (hoặc theo `buildingId` đã filter) |
| `occupancy.buildings[]` | Bảng / card theo từng building |
| `occupiedSlots`, `reservedSlots`, `availableSlots` | Breakdown dưới % |

> API `/dashboard/stats` còn trả sessions, reservations, users, incidents, revenue… — Peak Hour screen chỉ cần phần `occupancy` nếu chỉ hiển thị tỷ lệ lấp đầy.

### 2.3 Peak Hours cho Driver (REST)

Driver / Staff xem khung giờ đông **theo 1 building** trước khi đặt chỗ:

`GET /api/buildings/{buildingId}/peak-hours`

| Query | Bắt buộc | Mô tả |
|---|---|---|
| `fromDay` | Không | `YYYY-MM-DD` — mặc định = hôm nay − 6 ngày |
| `toDay` | Không | `YYYY-MM-DD` — mặc định = hôm nay |

**Auth:** Bearer JWT — role `DRIVER` \| `STAFF` \| `MANAGER` \| `ADMIN`

Response `data` cùng shape với manager (`PeakHourAnalysisResponse`): `peakHours`, `buckets`, `averagePerHour`, …

| AI | Endpoint | Scope |
|---|---|---|
| Manager dashboard | `GET /api/manager/dashboard/peak-hours` | Optional `buildingId` (có thể toàn hệ thống) |
| Driver / trước reservation | `GET /api/buildings/{buildingId}/peak-hours` | Bắt buộc 1 building |

### 2.4 Thông báo giờ cao điểm (Driver / Staff)

Push realtime (không thay REST ở trên). Khi building đang peak:

- Backend job `PeakHourNotificationJob` đẩy event WebSocket / notification: **`PEAK_HOUR_ALERT`**
- Khi tạo reservation trùng peak → event **`PEAK_HOUR_WARN`** cho driver
- Message gợi ý: *"Building đang trong giờ cao điểm."*
- Reservation trong giờ peak **không bị chặn** chỉ vì peak (vẫn phụ thuộc còn slot / building rule).

FE listen notification channel như các event khác (`VEHICLE_OVERSTAY`, `PAYMENT_REMINDER`, …).

---

## 3. Checklist tích hợp FE

### Maintenance screen
- [ ] PATCH status Building / Floor / Zone / Slot
- [ ] Disable “Bảo trì” khi slot `RESERVED` | `OCCUPIED` | `PENDING_EXIT`
- [ ] (Optional) Force-reset + xem occupancy detail slot

### Peak Hour screen (Manager)
- [ ] `GET /dashboard/peak-hours` → chart 24h + list `peakHours`
- [ ] `GET /dashboard/stats` → hiển thị `occupancy.occupancyRate` (+ breakdown)
- [ ] Filter `buildingId`, `fromDay`, `toDay`
- [ ] (Optional) Toast / banner khi nhận `PEAK_HOUR_ALERT`

### Peak Hour (Driver)
- [ ] `GET /api/buildings/{buildingId}/peak-hours` → banner / hint khi chọn giờ reservation
- [ ] Listen `PEAK_HOUR_WARN` / `PEAK_HOUR_ALERT`

---

## 4. Quick reference

```
# Maintenance
PATCH /api/manager/setup/buildings/{id}/status   body: { "status": "MAINTENANCE" }
PATCH /api/manager/setup/floors/{id}/status      body: { "status": "MAINTENANCE" }
PATCH /api/manager/setup/zones/{id}/status       body: { "status": "MAINTENANCE" }
PATCH /api/manager/setup/slots/{id}/status       body: { "status": "MAINTENANCE" }

# Peak Hour + Occupancy
GET /api/manager/dashboard/peak-hours?buildingId=&fromDay=&toDay=
GET /api/manager/dashboard/stats?buildingId=&fromDay=&toDay=
GET /api/buildings/{buildingId}/peak-hours?fromDay=&toDay=
```
