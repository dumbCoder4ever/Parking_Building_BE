# FE Guide — Building Rules

Tài liệu cho Frontend tích hợp **Building Rules** (luật vận hành theo từng building).

**Cập nhật:** 29/07/2026  
**Backend source:** `BuildingRuleService`, `BuildingRuleController`, `BuildingController`

---

## 1. Tóm tắt nhanh (đọc trước)

| Câu hỏi | Trả lời |
|---------|---------|
| Building Rule là gì? | Luật vận hành **theo từng building** (giờ hoạt động, không qua đêm, curfew loại xe, max giờ đỗ…) |
| Giờ mở/đóng cửa chính của building ở đâu? | Field `operatingStartTime` / `operatingEndTime` trên **Building** (set lúc tạo/sửa building) |
| `ruleValue` có bắt buộc không? | **Không** — trừ khi rule cần giá trị cụ thể (curfew, max hours, hoặc khoảng giờ tùy chỉnh) |
| BE có validate `ruleValue` theo giờ building không? | **Có** (từ 29/07/2026) — giờ trong `ruleValue` phải nằm trong giờ building |
| Lúc đặt chỗ, BE enforce theo gì? | `OPERATING_HOURS` / `NO_OVERNIGHT` → dùng **giờ building**, không đọc `ruleValue` |
| Driver có sửa rule không? | **Không** — chỉ đọc rule ACTIVE |

**Một câu:** Giờ building là **nguồn gốc**; rule là **mô tả / ràng buộc bổ sung**. FE form Manager phải validate `ruleValue` không vượt quá giờ building.

---

## 2. Phân vai rõ ràng (tránh nhầm)

| Khái niệm | Nguồn | Vai trò |
|-----------|-------|---------|
| Giờ hoạt động building | `Building.operatingStartTime` / `operatingEndTime` | Giờ mở–đóng cửa chính; **BE enforce** khi đặt chỗ / check-in |
| Rule `OPERATING_HOURS` | `building_rules.rule_value` (optional) | Mô tả / hiển thị; có thể là subset giờ trong giờ building |
| Rule `NO_OVERNIGHT` | `building_rules.rule_value` (optional) | Cờ “không qua đêm”; enforce runtime vẫn theo giờ building |
| Rule `MAX_PARKING_HOURS` | `ruleValue = "8"` | **Informational** lúc đặt chỗ; overstay job dùng riêng |
| Rule `VEHICLE_TYPE_CURFEW` | `ruleValue = "Truck:22:00"` | **Enforce** khi đặt chỗ / check-in với loại xe tương ứng |
| Price Policy `maxHours` | `pricing_policies.max_hours` | **Cap phí**, không phải building rule — xem `FE_PRICING_POLICY_MAX_HOURS.md` |

---

## 3. API

**Auth:** Bearer JWT  
**Wrapper response:**

```json
{
  "success": true,
  "message": "...",
  "data": { }
}
```

Lỗi validation → HTTP **400**, `success: false`, message mô tả lỗi.

### 3.1 Manager — CRUD rule

```
Base: /api/manager/buildings/{buildingId}/rules
Role: MANAGER | ADMIN
```

| Method | Path | Mô tả |
|--------|------|-------|
| `GET` | `/api/manager/buildings/{buildingId}/rules` | List rule **ACTIVE** của building |
| `POST` | `/api/manager/buildings/{buildingId}/rules` | Tạo rule |
| `PUT` | `/api/manager/buildings/{buildingId}/rules/{ruleId}` | Cập nhật rule |
| `DELETE` | `/api/manager/buildings/{buildingId}/rules/{ruleId}` | Xóa rule |

**Lưu ý:** `ruleCode` **không đổi** khi update (chỉ có trong body create).

### 3.2 Driver / Staff — đọc rule (read-only)

```
GET /api/buildings/{buildingId}/rules
Role: DRIVER | STAFF | MANAGER | ADMIN
```

Trả về danh sách rule **ACTIVE** — dùng trước khi tạo reservation.

### 3.3 Lấy giờ building (cho form validate)

Manager form cần `operatingStartTime` / `operatingEndTime`:

```
GET /api/manager/setup/buildings/{buildingId}
```

Hoặc từ list building / availability (Driver):

```
GET /api/buildings/available
```

Response có field:

```json
{
  "operatingStartTime": "06:00:00",
  "operatingEndTime": "23:00:00",
  "operatingHoursDisplay": "06:00 - 23:00"
}
```

---

## 4. Request / Response schema

### 4.1 Create — `CreateBuildingRuleRequest`

```json
{
  "ruleCode": "OPERATING_HOURS",
  "title": "Giờ hoạt động bãi xe",
  "description": "Chỉ nhận xe trong khung giờ này",
  "ruleValue": "08:00-22:00",
  "status": "ACTIVE"
}
```

| Field | Bắt buộc | Ghi chú |
|-------|----------|---------|
| `ruleCode` | Có | Một trong 4 mã bên dưới |
| `title` | Có | Hiển thị cho Driver |
| `description` | Không | Mô tả thêm |
| `ruleValue` | Tùy rule | Format theo `ruleCode` — xem mục 5 |
| `status` | Không | `ACTIVE` (default) hoặc `INACTIVE` |

### 4.2 Update — `UpdateBuildingRuleRequest`

```json
{
  "title": "Giờ hoạt động bãi xe",
  "description": "...",
  "ruleValue": "08:00-22:00",
  "status": "ACTIVE"
}
```

Không có field `ruleCode`.

### 4.3 Response — `BuildingRuleResponse`

```json
{
  "ruleId": "uuid",
  "buildingId": "uuid",
  "ruleCode": "OPERATING_HOURS",
  "title": "Giờ hoạt động bãi xe",
  "description": "...",
  "ruleValue": "08:00-22:00",
  "status": "ACTIVE",
  "createdAt": "2026-07-29T10:00:00",
  "updatedAt": "2026-07-29T10:00:00"
}
```

---

## 5. `ruleCode` và format `ruleValue`

### 5.1 Bảng tổng hợp

| ruleCode | ruleValue | Validate BE | Enforce lúc đặt chỗ |
|----------|-----------|-------------|---------------------|
| `OPERATING_HOURS` | Optional. `HH:mm-HH:mm` hoặc để trống | Nếu có → phải trong giờ building | Có — theo **giờ building** |
| `NO_OVERNIGHT` | Optional. `HH:mm-HH:mm` hoặc `HH:mm` (cutoff) | Nếu có → phải trong giờ building | Có — theo **giờ building** |
| `MAX_PARKING_HOURS` | `"8"` (số nguyên dương) | Bắt buộc nếu muốn có ý nghĩa | **Không** chặn reservation |
| `VEHICLE_TYPE_CURFEW` | `"Truck:22:00"` | Curfew trong giờ building | Có — theo `ruleValue` |

### 5.2 `OPERATING_HOURS` / `NO_OVERNIGHT`

**Format range:** `HH:mm-HH:mm` hoặc `HH:mm:ss-HH:mm:ss`

```
✅ 08:00-22:00        (building 06:00-23:00)
✅ 06:00-23:00        (bằng giờ building)
✅ (để trống)         (BE chấp nhận)
❌ 01:00-05:00        (ngoài giờ building)
❌ 22:00-08:00        (end phải sau start)
❌ abc-def            (format sai)
```

**Format single time** (thường dùng cho `NO_OVERNIGHT` — giờ cutoff):

```
✅ 22:00              (trong giờ building)
❌ 01:00              (ngoài giờ building)
```

**Điều kiện tiên quyết:** Building phải đã có `operatingStartTime` và `operatingEndTime`. Nếu chưa → BE trả:

```
Building operating hours must be configured before setting time-based rules
```

### 5.3 `VEHICLE_TYPE_CURFEW`

**Format:** `TypeName:HH:mm`

```
✅ Truck:22:00
✅ Motorbike:21:30
❌ Truck:01:00       (curfew ngoài giờ building)
❌ Truck             (thiếu giờ)
❌ :22:00            (thiếu tên loại xe)
```

`TypeName` phải khớp `vehicleType.typeName` (không phân biệt hoa thường lúc enforce).

### 5.4 `MAX_PARKING_HOURS`

**Format:** số nguyên dương (string)

```
✅ "8"
✅ "24"
❌ "0"
❌ "-5"
❌ "8.5"
❌ "abc"
```

Dùng cho **hiển thị** (“Không đỗ quá 8 giờ”) và job overstay — **không** chặn tạo reservation theo số giờ này.

---

## 6. Validation FE (client-side)

FE **nên** validate trước khi gọi API để UX tốt hơn. Logic mirror BE:

### 6.1 Helper parse time

```typescript
/** Parse "HH:mm" hoặc "HH:mm:ss" → phút trong ngày */
function parseTimeToMinutes(value: string): number | null {
  const m = value.trim().match(/^(\d{2}):(\d{2})(?::(\d{2}))?$/);
  if (!m) return null;
  return parseInt(m[1], 10) * 60 + parseInt(m[2], 10);
}

function isWithinBuildingHours(
  time: string,
  buildingStart: string,
  buildingEnd: string
): boolean {
  const t = parseTimeToMinutes(time);
  const start = parseTimeToMinutes(buildingStart);
  const end = parseTimeToMinutes(buildingEnd);
  if (t == null || start == null || end == null) return false;
  return t >= start && t <= end;
}
```

### 6.2 Validate theo ruleCode

```typescript
type RuleCode =
  | 'OPERATING_HOURS'
  | 'NO_OVERNIGHT'
  | 'MAX_PARKING_HOURS'
  | 'VEHICLE_TYPE_CURFEW';

function validateRuleValue(
  ruleCode: RuleCode,
  ruleValue: string | undefined | null,
  buildingStart: string,
  buildingEnd: string
): string | null {
  const v = ruleValue?.trim() ?? '';
  if (!v) return null; // optional cho hầu hết rule

  switch (ruleCode) {
    case 'OPERATING_HOURS':
    case 'NO_OVERNIGHT': {
      if (v.includes('-')) {
        const [start, end] = v.split('-', 2).map((s) => s.trim());
        if (!start || !end) return 'Định dạng: HH:mm-HH:mm (vd. 08:00-22:00)';
        const startMin = parseTimeToMinutes(start);
        const endMin = parseTimeToMinutes(end);
        if (startMin == null || endMin == null) return 'Giờ không hợp lệ (HH:mm)';
        if (endMin <= startMin) return 'Giờ kết thúc phải sau giờ bắt đầu';
        if (!isWithinBuildingHours(start, buildingStart, buildingEnd))
          return `Giờ bắt đầu phải trong ${buildingStart} - ${buildingEnd}`;
        if (!isWithinBuildingHours(end, buildingStart, buildingEnd))
          return `Giờ kết thúc phải trong ${buildingStart} - ${buildingEnd}`;
        return null;
      }
      if (!isWithinBuildingHours(v, buildingStart, buildingEnd))
        return `Giờ phải trong ${buildingStart} - ${buildingEnd}`;
      return null;
    }

    case 'VEHICLE_TYPE_CURFEW': {
      const parts = v.split(':');
      if (parts.length < 3) return 'Định dạng: TypeName:HH:mm (vd. Truck:22:00)';
      const typeName = parts[0].trim();
      const time = `${parts[1]}:${parts[2]}`;
      if (!typeName) return 'Tên loại xe không được trống';
      if (!isWithinBuildingHours(time, buildingStart, buildingEnd))
        return `Curfew phải trong ${buildingStart} - ${buildingEnd}`;
      return null;
    }

    case 'MAX_PARKING_HOURS': {
      const n = Number(v);
      if (!Number.isInteger(n) || n <= 0) return 'Nhập số giờ nguyên dương (vd. 8)';
      return null;
    }

    default:
      return null;
  }
}
```

### 6.3 Hiển thị lỗi từ BE

Luôn fallback hiển thị `response.message` khi `success === false`:

```typescript
// axios example
catch (err) {
  const msg = err.response?.data?.message ?? 'Không thể lưu rule';
  toast.error(msg);
}
```

**Message BE thường gặp:**

| Message | Nguyên nhân |
|---------|-------------|
| `Rule start time must be within building operating hours 06:00 - 23:00` | Giờ rule ngoài giờ building |
| `Rule end time must be after rule start time` | End ≤ start |
| `Invalid ruleValue format. Expected HH:mm-HH:mm` | Sai format range |
| `Invalid ruleValue format. Expected TypeName:HH:mm` | Sai format curfew |
| `MAX_PARKING_HOURS ruleValue must be a positive integer` | Không phải số nguyên dương |
| `Building operating hours must be configured before setting time-based rules` | Building chưa có giờ mở/đóng |
| `Invalid ruleCode. Allowed: [...]` | ruleCode không hợp lệ |

---

## 7. Gợi ý UI Manager

### 7.1 Form tạo / sửa rule

1. **Load building trước** → lấy `operatingStartTime`, `operatingEndTime`.
2. Hiển thị banner cố định:
   > Giờ hoạt động building: **06:00 – 23:00**  
   > Rule có giờ tùy chỉnh phải nằm trong khoảng này.
3. Dropdown `ruleCode` → đổi placeholder / hint `ruleValue`:

| ruleCode | Placeholder ruleValue |
|----------|----------------------|
| `OPERATING_HOURS` | `08:00-22:00` (optional) |
| `NO_OVERNIGHT` | `22:00` hoặc `08:00-22:00` (optional) |
| `MAX_PARKING_HOURS` | `8` |
| `VEHICLE_TYPE_CURFEW` | `Truck:22:00` |

4. Validate on blur / submit — disable nút Lưu nếu lỗi client.
5. Nếu building **chưa có giờ** → disable form rule có thời gian, link sang trang sửa building.

### 7.2 `OPERATING_HOURS` — có nên cho nhập giờ?

| Cách làm | Ưu | Nhược |
|----------|-----|-------|
| **Để trống `ruleValue`**, chỉ nhập title/description | Đơn giản; khớp BE enforce (dùng giờ building) | Không hiển thị subset giờ trên rule |
| **Cho nhập range** subset giờ building | Linh hoạt hiển thị | User dễ nhầm — enforce vẫn theo giờ building, không theo ruleValue |

**Khuyến nghị:** Với `OPERATING_HOURS`, ưu tiên hiển thị giờ từ **building** trên UI Driver; `ruleValue` chỉ dùng khi Manager muốn mô tả subset (vd. “Chỉ nhận xe 8h–22h” trong khi building mở 6h–23h).

### 7.3 Màn Driver

- Gọi `GET /api/buildings/{id}/rules` trước reservation.
- Hiển thị `title` (+ `description` nếu có).
- Giờ hoạt động chính: lấy từ `operatingHoursDisplay` trên building/slot availability — **không** parse `ruleValue` làm giờ chính thức.

---

## 8. Enforce runtime (FE cần biết khi báo lỗi đặt chỗ)

Khi Driver đặt chỗ / check-in, BE gọi `BuildingRuleService.validateForEntry`:

| ruleCode ACTIVE | Hành vi |
|-----------------|---------|
| `OPERATING_HOURS` | Chặn nếu `reservationStart` **ngoài** `building.operatingStartTime` – `operatingEndTime` |
| `NO_OVERNIGHT` | Giống trên |
| `VEHICLE_TYPE_CURFEW` | Chặn nếu loại xe khớp và giờ ≥ curfew trong `ruleValue` |
| `MAX_PARKING_HOURS` | **Không** chặn lúc đặt chỗ |

**Lỗi enforce ví dụ** (HTTP 400):

```
Building rule violated (OPERATING_HOURS): outside operating hours 06:00 - 23:00
Building rule violated (VEHICLE_TYPE_CURFEW): Truck not allowed after 22:00
```

FE reservation form: nên disable chọn giờ ngoài `operatingStartTime`–`operatingEndTime` nếu building có rule `OPERATING_HOURS` / `NO_OVERNIGHT` active.

---

## 9. Ví dụ API

### 9.1 Tạo rule thành công

**Request**

```http
POST /api/manager/buildings/{buildingId}/rules
Authorization: Bearer {token}
Content-Type: application/json

{
  "ruleCode": "OPERATING_HOURS",
  "title": "Giờ hoạt động",
  "ruleValue": "08:00-22:00",
  "status": "ACTIVE"
}
```

**Response** `201 Created`

```json
{
  "success": true,
  "message": "Building rule created successfully",
  "data": {
    "ruleId": "...",
    "buildingId": "...",
    "ruleCode": "OPERATING_HOURS",
    "title": "Giờ hoạt động",
    "ruleValue": "08:00-22:00",
    "status": "ACTIVE"
  }
}
```

### 9.2 Tạo rule fail — giờ ngoài building

**Request** (building `06:00-23:00`):

```json
{
  "ruleCode": "OPERATING_HOURS",
  "title": "Giờ hoạt động",
  "ruleValue": "01:00-05:00",
  "status": "ACTIVE"
}
```

**Response** `400 Bad Request`

```json
{
  "success": false,
  "message": "Rule start time must be within building operating hours 06:00 - 23:00",
  "data": null
}
```

### 9.3 Curfew hợp lệ

```json
{
  "ruleCode": "VEHICLE_TYPE_CURFEW",
  "title": "Xe tải không vào sau 22h",
  "ruleValue": "Truck:22:00",
  "status": "ACTIVE"
}
```

### 9.4 Max parking hours

```json
{
  "ruleCode": "MAX_PARKING_HOURS",
  "title": "Tối đa 8 giờ",
  "ruleValue": "8",
  "status": "ACTIVE"
}
```

---

## 10. Checklist tích hợp FE

### Manager UI

- [ ] Load `operatingStartTime` / `operatingEndTime` khi mở form rule
- [ ] Hiển thị giờ building làm reference
- [ ] Validate `ruleValue` client-side theo mục 6
- [ ] Hint / placeholder đổi theo `ruleCode`
- [ ] Hiển thị `response.message` khi BE trả 400
- [ ] Block tạo rule có giờ nếu building chưa config giờ mở/đóng

### Driver UI

- [ ] `GET /api/buildings/{id}/rules` — hiển thị rule ACTIVE
- [ ] Giờ hoạt động chính từ building (`operatingHoursDisplay`)
- [ ] Time picker reservation nằm trong giờ building (nếu có rule OPERATING_HOURS / NO_OVERNIGHT)
- [ ] Không nhầm `MAX_PARKING_HOURS` rule với Price Policy `maxHours`

---

## 11. Liên kết tài liệu liên quan

| Tài liệu | Nội dung |
|----------|----------|
| `FE_PRICING_POLICY_MAX_HOURS.md` | Phân biệt `maxHours` policy vs `MAX_PARKING_HOURS` rule |
| `FE_MAINTENANCE_AND_PEAK_HOUR.md` | Maintenance / peak hour (reservation không bị chặn chỉ vì peak) |
| `docs/db/create_building_rules_table.sql` | Schema bảng `building_rules` |
