# FE Fix — Staff Checkout từ màn Incident (403)

> Màn: `/staff/incidents` → Review Incident → Checkout  
> Lỗi hiện tại: `POST /api/sessions/driver/checkout?sessionId=...` → **403 Forbidden** (body rỗng)

---

## 1. Nguyên nhân 403 (body rỗng)

403 **không có JSON** = request bị chặn **trước** business logic (Spring Security / sai format request).

| Nguyên nhân | Cách nhận biết |
|---|---|
| Gửi POST không phải `multipart/form-data` | Headers không có `Content-Type: multipart/form-data; boundary=...` |
| Thiếu `Authorization: Bearer ...` | Tab Headers không có token |
| Token không phải Staff | JWT role ≠ `STAFF` / `MANAGER` / `ADMIN` |
| Gọi checkout **trước** khi resolve incident | Sau khi sửa 403, sẽ gặp **400** có message (khác lỗi) |

**Lưu ý:** BE endpoint **bắt buộc** `consumes = multipart/form-data`. Gửi query string + POST trống → dễ fail.

---

## 2. API checkout đúng (Staff — mất vé / sau incident)

### Endpoint

```
POST /api/sessions/driver/checkout
Content-Type: multipart/form-data
Authorization: Bearer <STAFF_TOKEN>
Role: STAFF | MANAGER | ADMIN
```

### Body (FormData — bắt buộc)

| Field | Bắt buộc | Ghi chú |
|---|---|---|
| `sessionId` | ✅ (flow mất vé) | UUID session từ `session-evidence` |
| `ticketCode` | ✅ (flow có vé) | Dùng **một trong hai**: `sessionId` **hoặc** `ticketCode` |
| `checkoutImage` | ❌ | File ảnh xe ra (optional) |

### ❌ Sai (hay gặp)

```javascript
// SAI — chỉ query, không FormData
axios.post(`/api/sessions/driver/checkout?sessionId=${id}`);

// SAI — JSON body
axios.post('/api/sessions/driver/checkout', { sessionId: id });

// SAI — token Driver trên màn Staff
axios.post(url, form, { headers: { Authorization: driverToken } });
```

### ✅ Đúng

```javascript
async function staffCheckoutAfterLostTicket(sessionId, staffToken, checkoutImageFile) {
  const form = new FormData();
  form.append('sessionId', sessionId);

  if (checkoutImageFile) {
    form.append('checkoutImage', checkoutImageFile);
  }

  const { data } = await axios.post(
    '/api/sessions/driver/checkout',
    form,
    {
      headers: {
        Authorization: `Bearer ${staffToken}`,
        // KHÔNG set Content-Type tay — browser/axios tự thêm boundary
      },
    }
  );

  return data; // { success, message, data: CheckoutResponse }
}
```

### TypeScript helper (axios instance dùng chung)

```typescript
export async function driverCheckoutBySession(
  sessionId: string,
  checkoutImage?: File
) {
  const form = new FormData();
  form.append('sessionId', sessionId);
  if (checkoutImage) form.append('checkoutImage', checkoutImage);

  return api.post<ApiResponse<CheckoutResponse>>(
    '/api/sessions/driver/checkout',
    form
  );
}
```

Đảm bảo `api` interceptor đã gắn **Staff token** (cùng token dùng cho `/api/incidents/...`).

---

## 3. Flow đầy đủ — DRIVER_LOST_TICKET (mất vé)

Staff **không được** bấm Checkout khi incident còn `OPEN`.

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Driver tạo report                                        │
│    POST /api/incidents/driver                               │
│    { sessionId, incidentType: "DRIVER_LOST_TICKET", ... }   │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Staff mở incident → load bằng chứng                      │
│    GET /api/incidents/{incidentId}/session-evidence         │
│    → lấy sessionId, vehiclePlate, checkinVehicleImage, phí  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Staff nhận xử lý                                         │
│    PUT /api/incidents/{id}/status?status=IN_PROGRESS        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Staff xác minh biển + vé (chỉ DRIVER_LOST_TICKET)         │
│    POST /api/incidents/{id}/verify-vehicle                │
│    Body JSON: { "plateNumber": "30A-12345",                 │
│                 "ticketCode": "T-XXX" }  // ticket optional   │
│    → verificationResult phải = "MATCH"                      │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. Staff resolve — bắt buộc AUTHORIZE_CHECKOUT              │
│    PUT /api/incidents/{id}/status?status=RESOLVED           │
│    Body JSON:                                               │
│    {                                                        │
│      "resolutionAction": "AUTHORIZE_CHECKOUT",              │
│      "resolution": "Da xac minh, cho phep checkout"         │
│    }                                                        │
│    → BE set session.incidentAuthorized = true               │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. Staff checkout (FormData — section 2)                    │
│    POST /api/sessions/driver/checkout                       │
│    form: sessionId                                          │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. (Optional) Đóng incident                                 │
│    PUT /api/incidents/{id}/status?status=CLOSED             │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Điều kiện enable nút Checkout (UI)

```typescript
const canCheckout =
  incident.status === 'RESOLVED' &&
  incident.resolutionAction === 'AUTHORIZE_CHECKOUT' &&
  (incident.incidentType !== 'DRIVER_LOST_TICKET' ||
    incident.verificationResult === 'MATCH') &&
  sessionEvidence.sessionActive === true;
```

| Badge session | Ý nghĩa |
|---|---|
| `PENDING_PAYMENT` + `UNPAID` | Bình thường trước checkout — CASH sẽ tạo payment lúc checkout |
| Sau bước 5 | `incidentAuthorized = true` — mới được gọi checkout bằng `sessionId` |

---

## 5. Các flow incident khác (không dùng driver/checkout)

| incidentType | Resolve action | Checkout API |
|---|---|---|
| `DRIVER_LOST_TICKET` | `AUTHORIZE_CHECKOUT` | `POST /api/sessions/driver/checkout` + `sessionId` |
| `DRIVER_CANNOT_FIND_VEHICLE` | `PROVIDE_VEHICLE_LOCATION` | Không checkout — chỉ đóng incident |
| `DRIVER_INCORRECT_FEE` | `UPDATE_PAYMENT` + `adjustedAmount` | Sau đó checkout bình thường (có vé) |
| `DRIVER_SLOT_OCCUPIED` | `REASSIGN_SLOT` + `newSlotId` | Không cần checkout |

**Checkout có vé (driver đã PAID điện tử):**

```
PATCH /api/sessions/{sessionId}/confirm-exit
Content-Type: multipart/form-data
Role: STAFF+
```

**Checkout staff thông thường (có ticketCode):**

```
POST /api/sessions/checkout
FormData: ticketCode, paymentMethod?, plateImage?, checkoutImage?
```

---

## 6. Debug checklist (DevTools)

Khi vẫn 403, chụp gửi BE:

- [ ] Request URL đúng `/api/sessions/driver/checkout` (không thiếu `/api`)
- [ ] Request Headers có `Authorization: Bearer ...`
- [ ] Request Headers có `Content-Type: multipart/form-data; boundary=...`
- [ ] Payload / Form Data có field `sessionId`
- [ ] JWT decode: role = `STAFF` (không phải `DRIVER`)
- [ ] Incident đã `RESOLVED` + `AUTHORIZE_CHECKOUT`

---

## 7. Lỗi sau khi hết 403 (400 — có message JSON)

| Message BE | FE xử lý |
|---|---|
| `Session chưa được authorize qua incident resolution...` | Chưa bước 5 — resolve `AUTHORIZE_CHECKOUT` |
| `Session is not active` | Session đã checkout rồi — refresh |
| `You are not assigned to this building` | Staff sai building — đăng nhập staff đúng tòa nhà |
| `Payment has not been completed yet...` | Driver chưa PAID — dùng flow thanh toán trước |

---

## 8. Response mẫu thành công

```json
{
  "success": true,
  "message": "Driver checkout successful",
  "data": {
    "sessionId": "f7fff060-a58e-4ffc-a78a-53566eeeeb12",
    "totalFee": 50000,
    "paymentMethod": "CASH",
    "sessionStatus": "COMPLETED"
  }
}
```

---

## 9. Tóm tắt 1 dòng cho FE

> **Màn incident staff checkout mất vé:** resolve incident `AUTHORIZE_CHECKOUT` trước → `POST /api/sessions/driver/checkout` bằng **`FormData(sessionId)`** + **Staff JWT** — không gửi POST trống / JSON.

---

*Tham chiếu BE: `ParkingSessionController.driverCheckout`, `ParkingSessionService.driverCheckoutBySession`, `IncidentServiceImpl` (AUTHORIZE_CHECKOUT).*
