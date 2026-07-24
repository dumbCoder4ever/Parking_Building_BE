# FE Guide — Incident / Driver Report (luồng mới)

Tài liệu cho Frontend tích hợp **xử lý Driver Report & bằng chứng session** (cập nhật BE 24/07/2026).

**Base path:** `/api/incidents`  
**Auth:** Bearer JWT

| Role | Màn hình |
|---|---|
| `DRIVER` | Tạo report, xem report của mình |
| `STAFF` | Xử lý report (chỉ building được assign) |
| `MANAGER` / `ADMIN` | Xem/xử lý toàn bộ |

**Wrapper response** (endpoint có `ApiResponse`):

```json
{
  "success": true,
  "message": "...",
  "data": { }
}
```

Endpoint trả thẳng DTO (không wrapper): `verify-vehicle`, `validate-reassign`, `GET /`, `GET /{id}`, `PUT /{id}/status`, `POST /` (staff).

---

## 1. Thay đổi quan trọng (đọc trước khi code)

### 1.1 Nguồn bằng chứng — session là chính, reservation là phụ

| Trước | Sau |
|---|---|
| Staff chủ yếu gọi `latest-reservation` | Staff **gọi `session-evidence` trước** |
| Reservation là nguồn chính | **Session active** là nguồn chính (biển, ảnh check-in, vị trí, phí) |
| — | `latest-reservation` chỉ **bổ sung** / cross-check |

**Gợi ý UI Staff:** Mở incident → load `session-evidence` → hiển thị panel chính. Nếu cần thêm reservation → gọi `latest-reservation` (có thể 404 nếu driver không có reservation active).

### 1.2 Verify vehicle (DRIVER_LOST_TICKET)

- Session phải **`ACTIVE` hoặc `PENDING_PAYMENT`** — session đã checkout/completed → API trả lỗi.
- Với report từ Driver (`reportSource = DRIVER`): thêm check **reporter = driver của session**.
- Response có thêm: `checkinVehicleImage`, `checkoutVehicleImage`, `driverEmail`, `driverOwnershipVerified`.

### 1.3 Reservation active bao gồm CHECKED_IN

Status reservation được coi là active: `PENDING`, `APPROVED`, **`CHECKED_IN`**.

Ảnh hưởng: `latest-reservation`, reassign slot, resolve reservation fallback.

### 1.4 Phí hiển thị trên evidence

- `sessionTotalFee`: nếu session chưa checkout (`totalFee = 0`) → BE trả **`estimatedFee`** thay vì 0.
- FE hiển thị label kiểu "Phí ước tính" khi `sessionTotalFee === sessionEstimatedFee` và chưa PAID.

### 1.5 Staff chỉ thấy incident thuộc building được assign

`GET /api/incidents` (staff list) **lọc theo `BuildingStaff`**. Incident không resolve được `buildingId` → không hiện trong list.

---

## 2. Driver Report — 4 loại

| `incidentType` | Mô tả | `resolutionAction` khi RESOLVED |
|---|---|---|
| `DRIVER_LOST_TICKET` | Mất vé | `AUTHORIZE_CHECKOUT` |
| `DRIVER_CANNOT_FIND_VEHICLE` | Không tìm thấy xe | `PROVIDE_VEHICLE_LOCATION` |
| `DRIVER_INCORRECT_FEE` | Sai phí | `UPDATE_PAYMENT` (+ `adjustedAmount`) |
| `DRIVER_SLOT_OCCUPIED` | Slot bị chiếm | `REASSIGN_SLOT` (+ `newSlotId`) hoặc `NO_SLOT_AVAILABLE` |

### 2.1 Driver tạo report

```
POST /api/incidents/driver
Role: DRIVER
```

**Request:**

```json
{
  "sessionId": "session-uuid",
  "incidentType": "DRIVER_LOST_TICKET",
  "description": "Mat ve vat ly"
}
```

**Response:** `ApiResponse<IncidentResponse>`

### 2.2 Driver xem report của mình

```
GET /api/incidents/driver/me
Role: DRIVER
```

---

## 3. Staff — Workflow status

```
OPEN → IN_PROGRESS → RESOLVED → CLOSED
         ↘ CANCELLED (cần cancelReason)
```

| Transition | Hợp lệ? |
|---|---|
| OPEN → IN_PROGRESS | ✅ |
| OPEN → CANCELLED | ✅ |
| OPEN → RESOLVED / CLOSED | ❌ |
| IN_PROGRESS → RESOLVED | ✅ |
| IN_PROGRESS → CANCELLED | ✅ |
| IN_PROGRESS → CLOSED | ❌ |
| RESOLVED → CLOSED | ✅ |
| RESOLVED → CANCELLED | ❌ |
| CLOSED / CANCELLED → * | ❌ |

```
PUT /api/incidents/{incidentId}/status?status=IN_PROGRESS
PUT /api/incidents/{incidentId}/status?status=RESOLVED
PUT /api/incidents/{incidentId}/status?status=CLOSED
PUT /api/incidents/{incidentId}/status?status=CANCELLED
Role: STAFF (building assign) | MANAGER | ADMIN
```

**Body khi RESOLVED / CANCELLED:**

```json
{
  "resolutionAction": "AUTHORIZE_CHECKOUT",
  "resolution": "Da xac minh bien so, cho phep checkout",
  "adjustedAmount": 45000,
  "newSlotId": "slot-uuid",
  "cancelReason": "Bao cao trung lap"
}
```

| Field | Khi nào bắt buộc |
|---|---|
| `cancelReason` | `status=CANCELLED` |
| `resolutionAction` | `status=RESOLVED` (khuyến nghị) |
| `adjustedAmount` | `resolutionAction=UPDATE_PAYMENT` (max 10× estimatedFee) |
| `newSlotId` | `resolutionAction=REASSIGN_SLOT` |

**`resolutionAction` constants:**

- `AUTHORIZE_CHECKOUT` — set `session.incidentAuthorized = true`
- `PROVIDE_VEHICLE_LOCATION` — ghi log, staff trả lời driver
- `UPDATE_PAYMENT` — cập nhật `estimatedFee` + `totalFee`
- `REASSIGN_SLOT` — đổi slot session (+ reservation nếu có)
- `REJECT` — từ chối report
- `NO_SLOT_AVAILABLE` — không còn slot thay thế

---

## 4. API mới / cập nhật — Staff Evidence

### 4.1 ★ Session evidence (NGUỒN CHÍNH — MỚI)

```
GET /api/incidents/{incidentId}/session-evidence
Role: STAFF | MANAGER | ADMIN
```

**Response:** `ApiResponse<SessionEvidenceResponse>`

```json
{
  "success": true,
  "message": "Session evidence retrieved",
  "data": {
    "incidentId": "inc-xxx",
    "sessionId": "sess-xxx",
    "sessionStatus": "ACTIVE",
    "sessionActive": true,
    "checkinTime": "2026-07-24T10:00:00",
    "checkinVehicleImage": "https://cloudinary.../checkin.jpg",
    "checkoutVehicleImage": "https://cloudinary.../checkout.jpg",

    "vehicleId": "veh-xxx",
    "vehiclePlate": "30A-12345",
    "vehicleType": "Car",
    "driverUserId": "user-xxx",
    "driverEmail": "driver@test.com",
    "driverFullName": "Nguyen Van A",
    "driverMatchesReporter": true,

    "buildingId": "bld-1",
    "buildingName": "Building A",
    "floorId": "floor-1",
    "floorName": "Tang 1",
    "floorLevel": 1,
    "zoneId": "zone-1",
    "zoneName": "Zone A",
    "slotId": "slot-1",
    "slotName": "A-01",

    "ticketCode": "T-ABC123",
    "sessionEstimatedFee": 50000,
    "sessionTotalFee": 50000,
    "sessionPaymentStatus": "UNPAID",

    "reservation": {
      "reservationId": "res-xxx",
      "reservationCode": "RS-001",
      "reservationStatus": "CHECKED_IN",
      "reservationStart": "2026-07-24T09:00:00",
      "slotName": "A-01",
      "vehiclePlate": "30A-12345",
      "ticketCode": "T-ABC123",
      "estimatedFee": 50000,
      "sessionEstimatedFee": 50000,
      "sessionTotalFee": 50000,
      "sessionPaymentStatus": "UNPAID"
    }
  }
}
```

**FE notes:**

- `reservation` có thể **`null`** (guest hoặc driver không còn reservation active) — vẫn đủ data từ session.
- Hiển thị `checkinVehicleImage` cho staff đối chiếu biển số.
- Sau checkout: `checkoutVehicleImage` có URL ảnh xe ra (null nếu chưa checkout).
- `driverMatchesReporter`: `false` → cảnh báo report có thể không phải chủ xe.
- `sessionActive = false` → session không còn ACTIVE/PENDING_PAYMENT.

---

### 4.2 Latest reservation (BỔ SUNG)

```
GET /api/incidents/{incidentId}/latest-reservation
Role: STAFF | MANAGER | ADMIN
```

**Response:** `ApiResponse<LatestReservationResponse>`

**Lỗi thường gặp:**

```json
{
  "success": false,
  "message": "Driver has no active reservation. Use session-evidence endpoint for session-based verification."
}
```

→ FE **fallback** sang `session-evidence`, không block màn hình.

**Fields mới so với trước:** `ticketCode`, `estimatedFee`, `sessionEstimatedFee`, `sessionTotalFee`, `sessionPaymentStatus`.

---

### 4.3 Verify vehicle (CẬP NHẬT)

```
POST /api/incidents/{incidentId}/verify-vehicle
Role: STAFF | MANAGER | ADMIN
Chỉ áp dụng: incidentType = DRIVER_LOST_TICKET
```

**Request:**

```json
{
  "plateNumber": "30A-12345",
  "ticketCode": "T-ABC123"
}
```

- `ticketCode` **optional**. Có ticket → phải khớp cả plate + ticket + driver ownership.
- Không có ticket → chỉ cần plate + driver ownership.

**Response:** `VerifyVehicleResponse` (không wrapper)

```json
{
  "incidentId": "inc-xxx",
  "verificationResult": "MATCH",
  "sessionPlateNumber": "30A-12345",
  "sessionTicketCode": "T-ABC123",
  "providedPlateNumber": "30A-12345",
  "providedTicketCode": "T-ABC123",
  "checkinVehicleImage": "https://cloudinary.../checkin.jpg",
  "checkoutVehicleImage": null,
  "driverEmail": "driver@test.com",
  "driverOwnershipVerified": true,
  "message": "Vehicle ownership verified successfully"
}
```

| `verificationResult` | Ý nghĩa UI |
|---|---|
| `MATCH` | ✅ Cho phép bước RESOLVED + AUTHORIZE_CHECKOUT |
| `MISMATCH` | ❌ Hiện `message`, không cho resolve authorize |

**Lỗi:**

- Session không active → `"Session is not active. Current status: COMPLETED"`
- Sai incident type → `"Vehicle verification is only applicable for DRIVER_LOST_TICKET incidents"`

---

### 4.4 Available slots cho reassign (DRIVER_SLOT_OCCUPIED)

```
GET /api/incidents/{incidentId}/available-slots-for-reassign
Role: STAFF | MANAGER | ADMIN
```

**Response:** `ApiResponse<AvailableSlotResponse[]>`

- Chỉ slot **`AVAILABLE`** trên **cùng floor** với reservation (cùng vehicle type).
- Loại slot đang là slot hiện tại của session.
- Loại slot có reservation PENDING/APPROVED active.

```json
{
  "slotId": "slot-2",
  "slotName": "A-02",
  "slotStatus": "AVAILABLE",
  "available": true,
  "hasActiveReservation": false,
  "inSameBuilding": true,
  "message": "Slot is available for reassignment",
  "floorName": "Tang 1",
  "zoneName": "Zone A"
}
```

---

### 4.5 Validate slot trước khi reassign

```
POST /api/incidents/validate-reassign?incidentId={id}&newSlotId={slotId}
Role: STAFF | MANAGER | ADMIN
```

**Response:** `SlotAvailabilityCheckResponse`

```json
{
  "slotId": "slot-2",
  "slotName": "A-02",
  "isAvailable": true,
  "hasActiveReservation": false,
  "isInSameBuilding": true,
  "isSameVehicleType": true,
  "message": "Slot is available for reassignment"
}
```

FE: gọi trước khi submit RESOLVED + `REASSIGN_SLOT`, hoặc tin `available: true` từ list.

---

## 5. Flow FE đề xuất theo loại report

### 5.1 DRIVER_LOST_TICKET

```
1. Driver POST /driver
2. Staff GET list → chọn incident → status IN_PROGRESS
3. Staff GET /session-evidence          ← panel chính
4. Staff nhập plate (+ ticket optional) → POST /verify-vehicle
5. Nếu MATCH → PUT status=RESOLVED, resolutionAction=AUTHORIZE_CHECKOUT
6. Staff checkout driver (session.incidentAuthorized = true)
7. PUT status=CLOSED
```

### 5.2 DRIVER_CANNOT_FIND_VEHICLE

```
1–3. Giống trên (session-evidence để lấy vị trí slot/floor/zone)
4. PUT RESOLVED + PROVIDE_VEHICLE_LOCATION + resolution (mô tả vị trí)
5. CLOSED
```

### 5.3 DRIVER_INCORRECT_FEE

```
1–3. session-evidence → hiển thị sessionEstimatedFee / sessionTotalFee
4. (Optional) latest-reservation → so sánh estimatedFee
5. PUT RESOLVED + UPDATE_PAYMENT + adjustedAmount
6. CLOSED
```

### 5.4 DRIVER_SLOT_OCCUPIED

```
1–3. session-evidence
4. GET /available-slots-for-reassign → staff chọn slot
5. POST /validate-reassign (optional pre-check)
6. PUT RESOLVED + REASSIGN_SLOT + newSlotId
   hoặc NO_SLOT_AVAILABLE nếu list rỗng
7. CLOSED
```

---

## 6. IncidentResponse — fields FE cần hiển thị

```typescript
interface IncidentResponse {
  incidentId: string;
  sessionId: string;
  ticketCode?: string;
  vehiclePlate?: string;
  incidentType: string;
  description?: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'CANCELLED';
  createdAt: string;
  reporterId?: string;       // email driver nếu reportSource=DRIVER
  reportSource?: 'DRIVER' | 'STAFF' | 'SYSTEM';
  resolution?: string;
  resolvedAt?: string;
  resolvedBy?: string;
  resolutionAction?: string;
  verificationResult?: 'PENDING' | 'MATCH' | 'MISMATCH';
  verifiedAt?: string;
  verifiedBy?: string;
}
```

---

## 7. API tham chiếu nhanh

| Method | Path | Role | Ghi chú |
|---|---|---|---|
| POST | `/api/incidents/driver` | DRIVER | Tạo report |
| GET | `/api/incidents/driver/me` | DRIVER | List report của mình |
| GET | `/api/incidents/driver/all?buildingId=` | STAFF+ | Driver reports; Staff chỉ thấy building được assign |
| GET | `/api/incidents` | STAFF+ | List (staff: theo building assign) |
| GET | `/api/incidents/{id}` | STAFF+ | Chi tiết |
| PUT | `/api/incidents/{id}/status?status=` | STAFF+ | Cập nhật workflow |
| **GET** | **`/api/incidents/{id}/session-evidence`** | **STAFF+** | **★ Bằng chứng chính (MỚI)** |
| GET | `/api/incidents/{id}/latest-reservation` | STAFF+ | Bằng chứng bổ sung |
| POST | `/api/incidents/{id}/verify-vehicle` | STAFF+ | Mat ve — xác minh |
| GET | `/api/incidents/{id}/available-slots-for-reassign` | STAFF+ | Slot thay thế |
| POST | `/api/incidents/validate-reassign` | STAFF+ | Pre-check slot |
| GET | `/api/incidents/by-session/{sessionId}` | Auth | Incidents theo session |

---

## 8. Error codes FE nên handle

| HTTP | Message (ví dụ) | Hành vi UI |
|---|---|---|
| 400 | Session is not active | Báo session đã kết thúc, không verify được |
| 400 | Cancel reason is required | Bắt nhập lý do hủy |
| 400 | Must transition through IN_PROGRESS first | Disable nút Resolve khi còn OPEN |
| 400 | Adjusted amount exceeds maximum allowed | Validate input phí |
| 403 | You do not have permission to access this incident | Staff sai building |
| 404 | Driver has no active reservation | Fallback session-evidence |
| 404 | Incident not found | Refresh list |

---

*Tài liệu bám `IncidentController`, `IncidentServiceImpl`, DTO `SessionEvidenceResponse` / `VerifyVehicleResponse` — nhánh `huy` cập nhật 24/07/2026.*
