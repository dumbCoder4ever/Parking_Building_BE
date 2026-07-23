# FE Guide — Slot Suggestion & Peak Hours

Tài liệu Frontend cho:

1. **Slot Suggestion** — gợi ý slot trống (Manager + Driver + Staff)  
2. **Peak Hours** — Manager dashboard + API riêng cho Driver

**Base wrapper response (mọi API):**

```json
{
  "success": true,
  "message": "...",
  "data": { }
}
```

---

## 1. Slot Suggestion

### 1.1 Mục đích

API gợi ý các slot **AVAILABLE** “tốt hơn” trong một building theo loại xe.

| Role | Dùng khi |
|---|---|
| **Driver** | Chọn chỗ khi tạo reservation — hiện top slot đề xuất |
| **Staff** | Hỗ trợ gán / gợi ý chỗ (guest hoặc hỗ trợ driver) |
| **Manager** | Analytics / vận hành |

| Có làm | Không làm |
|---|---|
| Chỉ **đề xuất** (list + score + reason) | Không tự gán / RESERVE slot |
| Heuristic: tầng thấp + zone occupancy thấp | Không AI model; không thay rule building |

> Khác flow incident reassign: reassign chỉ list slot **cùng floor** với reservation. Slot Suggestion chấm điểm toàn building theo `vehicleTypeId`.

### 1.2 So sánh 2 endpoint

| | Driver / Staff (khuyến nghị) | Manager analytics |
|---|---|---|
| Endpoint | `GET /api/buildings/{buildingId}/slot-suggestions` | `GET /api/manager/analytics/slot-suggestion` |
| Auth | `DRIVER`, `STAFF`, `MANAGER`, `ADMIN` | `MANAGER`, `ADMIN` |
| `buildingId` | Path — bắt buộc | Query — bắt buộc |
| `vehicleTypeId` | Query — bắt buộc | Query — bắt buộc |
| `limit` | Query — mặc định `5`, tối đa `20` | Giống |
| Response | Cùng `List<SlotSuggestionResponse>` | Cùng |
| Controller | `BuildingController` | `ManagerController` |

Cùng service: `SlotSuggestionService.suggest(...)`.

### 1.3 API cho Driver / Staff

```http
GET /api/buildings/{buildingId}/slot-suggestions?vehicleTypeId=&limit=5
Authorization: Bearer <token>
```

| Param | Bắt buộc | Mô tả |
|---|---|---|
| `buildingId` (path) | Có | Building đang xem / đặt chỗ |
| `vehicleTypeId` | Có | Loại xe của xe sẽ đỗ |
| `limit` | Không | Số slot trả về — mặc định `5`, tối đa `20` |

**Ví dụ:**

```http
GET /api/buildings/e18470f5-60f5-11f1-813e-546ceb8a1636/slot-suggestions?vehicleTypeId=<uuid>&limit=5
```

### 1.4 API Manager (giữ nguyên)

`GET /api/manager/analytics/slot-suggestion?buildingId=&vehicleTypeId=&limit=5`

**Auth:** Bearer JWT — role `MANAGER` hoặc `ADMIN`

### 1.5 Response `data`

`List<SlotSuggestionResponse>` — đã sort `score` giảm dần (cao = ưu tiên hơn).

```json
[
  {
    "slotId": "uuid",
    "slotName": "A-01",
    "zoneId": "uuid",
    "zoneName": "Zone A",
    "floorId": "uuid",
    "floorName": "Floor 1",
    "floorLevel": 1,
    "score": 84.0,
    "reason": "Ưu tiên tầng thấp (L1), zone occupancy 30%"
  }
]
```

| Field | Ý nghĩa cho FE |
|---|---|
| `slotId` / `slotName` | Slot được gợi ý |
| `zone*` / `floor*` | Vị trí hiển thị trên UI |
| `floorLevel` | Số tầng (badge “L1”, “L2”…) |
| `score` | Điểm ưu tiên (cao hơn = tốt hơn) |
| `reason` | Text giải thích — tooltip / subtitle |

List rỗng `[]` = không còn slot AVAILABLE phù hợp (không phải lỗi).

### 1.6 Cách tính score (tham khảo)

```
score = 100 - (floorLevel × 8) - (zoneOccupancyRatio × 40)
```

- `zoneOccupancyRatio` = `(OCCUPIED + RESERVED) / totalSlotsInZone`
- Tầng thấp hơn → score cao hơn  
- Zone ít đông hơn → score cao hơn  

### 1.7 Error

| Tình huống | Kết quả |
|---|---|
| Thiếu `vehicleTypeId` (hoặc `buildingId` trên manager query) | `400` |
| Không có slot trống | `200` + `data: []` |

### 1.8 Gợi ý UI

**Driver**
- Sau khi chọn building + loại xe → gọi slot-suggestions
- Hiện “Gợi ý cho bạn”: top 3–5 slot; tap → prefill `slotId` vào form reservation
- Vẫn cho phép chọn slot khác trên map/grid

**Staff**
- Màn hỗ trợ check-in / gán chỗ → hiện list gợi ý theo building + vehicle type
- Không thay auto-assign guest (`findFirstAvailable…`); suggestion chỉ hỗ trợ chọn tay khi cần

**Manager**
- Card / table analytics: score badge + reason
- Nút áp dụng chỉ prefill — vẫn gọi API reservation/session riêng để gán thật

---

## 2. Peak Hours

Phân tích **số check-in theo giờ (0–23)** trong khoảng ngày, đánh dấu khung giờ cao điểm.

Cùng service: `PeakHourService.analyze(...)`  
Cùng DTO: `PeakHourAnalysisResponse`

### 2.1 So sánh 2 endpoint

| | Manager | Driver |
|---|---|---|
| Endpoint | `GET /api/manager/dashboard/peak-hours` | `GET /api/buildings/{buildingId}/peak-hours` |
| Auth | `MANAGER`, `ADMIN` | `DRIVER`, `STAFF`, `MANAGER`, `ADMIN` |
| `buildingId` | Query — **optional** (null = toàn hệ thống) | Path — **bắt buộc** 1 building |
| Use case | Dashboard chart, báo cáo | Hint khi chọn giờ đặt chỗ |
| Controller | `ManagerController` | `BuildingController` |

### 2.2 Manager — Peak Hours dashboard

```http
GET /api/manager/dashboard/peak-hours?buildingId=&fromDay=&toDay=
Authorization: Bearer <manager-token>
```

| Query | Bắt buộc | Mô tả |
|---|---|---|
| `buildingId` | Không | Lọc 1 building; bỏ trống = toàn hệ thống |
| `fromDay` | Không | `YYYY-MM-DD` — mặc định = hôm nay − 6 ngày |
| `toDay` | Không | `YYYY-MM-DD` — mặc định = hôm nay |

### 2.3 Driver — Peak Hours theo building

```http
GET /api/buildings/{buildingId}/peak-hours?fromDay=&toDay=
Authorization: Bearer <driver-token>
```

| Param | Bắt buộc | Mô tả |
|---|---|---|
| `buildingId` (path) | Có | Building đang xem / sắp đặt chỗ |
| `fromDay` | Không | Giống manager |
| `toDay` | Không | Giống manager |

Gọi khi driver mở màn chọn building / chọn giờ reservation để hiện banner “Giờ cao điểm: 8h, 9h, 17h”.

### 2.4 Response `data` (chung)

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
| `peakHours` | Danh sách giờ (0–23) peak — label “Khung giờ đông nhất” |
| `buckets` | Đủ 24 phần tử — chart cột theo giờ |
| `buckets[].hour` | `0` … `23` |
| `buckets[].sessionCount` | Số check-in trong giờ đó |
| `buckets[].peak` | `true` → highlight trên chart |
| `averagePerHour` | Trung bình / giờ (tham khảo) |
| `peakThreshold` | Ngưỡng peak (tham khảo) |

**Cách đánh dấu peak (BE):**

- Giờ có `sessionCount >= average + stdDev × factor` (config `PEAK_HOUR_STDDEV_FACTOR`, mặc định `1.0`)  
- **Hoặc** thuộc top-3 giờ có count > 0 (để data thưa vẫn hiện peak)

Peak **không chặn** reservation — chỉ cảnh báo / hiển thị.

### 2.5 Occupancy kèm Peak (Manager only)

Màn Peak Hour Manager thường kèm tỷ lệ lấp đầy:

`GET /api/manager/dashboard/stats?buildingId=&fromDay=&toDay=`

Lấy `data.occupancy`:

```json
{
  "occupancy": {
    "totalSlots": 200,
    "availableSlots": 80,
    "occupiedSlots": 90,
    "reservedSlots": 20,
    "pendingExitSlots": 10,
    "occupancyRate": 55.0,
    "buildings": [ /* per-building breakdown */ ]
  }
}
```

`occupancyRate = (occupiedSlots + reservedSlots) / totalSlots * 100`

### 2.6 Notification realtime (Driver / Staff)

Ngoài REST, FE nên listen WebSocket / notification:

| Event | Khi nào | Gợi ý UI |
|---|---|---|
| `PEAK_HOUR_ALERT` | Job phát hiện building đang peak | Toast / banner building |
| `PEAK_HOUR_WARN` | Driver vừa tạo reservation trùng giờ peak | Toast: *"Building đang trong khung giờ cao điểm"* |

Payload `PEAK_HOUR_WARN` (ví dụ): `buildingId`, `buildingName`, `reservationStart`, `message`.

---

## 3. Checklist tích hợp FE

### Slot Suggestion (Driver / Staff)
- [ ] Sau chọn building + `vehicleTypeId` → `GET /api/buildings/{buildingId}/slot-suggestions`
- [ ] Render top suggestions (`score` + `reason`)
- [ ] Tap suggestion → prefill `slotId` (không auto-create reservation)
- [ ] Handle `data: []` (empty state)
- [ ] Driver vẫn được chọn slot khác trên grid

### Slot Suggestion (Manager)
- [ ] Form chọn `buildingId` + `vehicleTypeId` (+ optional `limit`)
- [ ] Gọi `GET /api/manager/analytics/slot-suggestion` **hoặc** endpoint building ở trên
- [ ] Render list theo `score` + show `reason`

### Peak Hours (Manager)
- [ ] Chart 24h từ `buckets` + highlight `peak === true`
- [ ] List / chip từ `peakHours`
- [ ] Filter `buildingId`, `fromDay`, `toDay`
- [ ] (Optional) `GET /dashboard/stats` → `occupancy.occupancyRate`

### Peak Hours (Driver)
- [ ] Sau khi chọn building → `GET /api/buildings/{buildingId}/peak-hours`
- [ ] Banner / hint cạnh time picker nếu giờ chọn ∈ `peakHours`
- [ ] Listen `PEAK_HOUR_WARN` / `PEAK_HOUR_ALERT`
- [ ] Không disable nút đặt chỗ chỉ vì peak

---

## 4. Quick reference

```http
# Slot Suggestion — Driver / Staff (buildingId trên path)
GET /api/buildings/{buildingId}/slot-suggestions?vehicleTypeId=&limit=5

# Slot Suggestion — Manager analytics
GET /api/manager/analytics/slot-suggestion?buildingId=&vehicleTypeId=&limit=5

# Peak Hours — Manager (buildingId optional)
GET /api/manager/dashboard/peak-hours?buildingId=&fromDay=&toDay=

# Peak Hours — Driver (buildingId bắt buộc trên path)
GET /api/buildings/{buildingId}/peak-hours?fromDay=&toDay=

# Occupancy (Manager, kèm màn Peak)
GET /api/manager/dashboard/stats?buildingId=&fromDay=&toDay=
```

---

## 5. File liên quan (BE)

| Thành phần | Path |
|---|---|
| Driver/Staff Slot Suggestion + Peak Hours | `BuildingController` |
| Manager analytics | `ManagerController` |
| Slot scoring | `SlotSuggestionService` |
| Peak analysis | `PeakHourService` |
| DTOs | `SlotSuggestionResponse`, `PeakHourAnalysisResponse`, `PeakHourBucketResponse` |
| Peak job notify | `PeakHourNotificationJob` |
| Warn khi reserve | `ReservationService` (`PEAK_HOUR_WARN`) |
