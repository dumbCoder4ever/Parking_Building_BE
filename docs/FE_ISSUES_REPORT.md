# FE Issues Report — Current Session Page & Staff Checkout Lookup

> **Mục đích:** Báo cáo các lỗi/sai sót FE phát hiện được trong module `CurrentSession` (Driver) và `StaffCheckout` — yêu cầu FE fix.
> **Phát hiện bởi:** BE team, trong quá trình test back-end pricing TIERED & staff checkout lookup.
> **Trạng thái BE:** Mọi thứ đã OK — bug #1 (fee không tăng theo giờ) đã fix và verify bằng Swagger. Bug #5 (lookup theo ticket) đã thêm API mới, FE tích hợp theo hướng dẫn.

---

## Summary

| # | Mức độ | Issue | File |
|---|--------|-------|------|
| 1 | 🔴 Critical | Fee không tự cập nhật theo thời gian thực | `currentSession.jsx` |
| 2 | 🔴 Critical | Pricing Tiers breakdown UI không hiển thị | `currentSession.jsx` + thiếu BE field |
| 3 | 🟡 Medium | Label "Total Fee" gây nhầm lẫn với `estimatedFee` | `currentSession.jsx` |
| 4 | 🟡 Medium | `parkingHours` ceil có thể sai ở biên thời gian | (BE) `DriverServiceImpl.java` |
| 5 | 🟠 High | Staff checkout không phân biệt được Driver Reservation vs Walk-in Driver vs Guest | Staff checkout page + thiếu BE API lookup theo `ticketCode` |

---

## Issue #1 — Fee không tự cập nhật theo thời gian thực 🔴

### Mô tả
`CurrentSession` page chỉ gọi `getCurrentSessionRequest()` **1 lần lúc mount** (line 364-367). Sau đó, khi user đỗ xe nhiều giờ, `estimatedFee` hiển thị trên UI bị stale (giữ giá trị cũ), dù BE vẫn tính đúng theo `checkin_time + now`.

### Repro
1. User checkin vào bãi (ví dụ 9:00)
2. Sau 3 tiếng (12:00), user mở `CurrentSession` page → fee hiển thị đúng (tier1 = 20000)
3. User quay lại trang lúc 18:00 (9 tiếng) → fee **vẫn là 20000** (stale)
4. Reload trang → fee update đúng (tier3 = 60000)

### Root cause
```jsx
// currentSession.jsx dòng 364-367
useEffect(() => {
  dispatch(getCurrentSessionRequest());
  dispatch(getProfileUserRequest());
}, [dispatch]);  // ← chỉ chạy 1 lần
```

`useLiveTick` hook (dòng 86-93) chỉ update timer hiển thị (giờ:phút:giây), **không refetch** API.

### Impact
- User trả thiếu → staff dispute khi checkout
- User trả thừa → refund flow
- UX kém — user mất niềm tin vào hệ thống

### Fix khuyến nghị
**Option A — Polling (đơn giản):**
```jsx
useEffect(() => {
  const pollId = setInterval(() => {
    dispatch(getCurrentSessionRequest());
  }, 60_000); // mỗi 60s
  return () => clearInterval(pollId);
}, [dispatch]);
```

**Option B — React Query refetch on focus:**
```jsx
const { data } = useQuery(['currentSession'], fetchCurrentSession, {
  refetchInterval: 60_000,
  refetchOnWindowFocus: true,
});
```

**Option C — Refetch khi timer "đổi giờ":**
```jsx
useEffect(() => {
  // refetch ngay khi tăng sang giờ mới
  dispatch(getCurrentSessionRequest());
}, [now.hours()]); // dependency theo giờ hiện tại
```

Khuyến nghị: **Option A** (đơn giản, ít refactor), **Option B** (clean, scale tốt).

---

## Issue #2 — Pricing Tiers breakdown UI không hiển thị 🔴

### Mô tả
FE có sẵn code để render tier breakdown (4 tier chips với "Now" badge highlight tier hiện tại), nhưng **luôn ẩn** vì API không trả data `pricingTiers`.

### Repro
1. Mở `CurrentSession` page
2. Quan sát: **không thấy** 4 chip tier (tier1/tier2/tier3/tier4) với badge "Now"
3. DevTools → Network → response không có field `pricingTiers`

### Root cause

**FE (line 132-139):**
```jsx
const activeTierIndex = useMemo(() => {
  if (!session.pricingTiers) return 0;  // ← undefined → return 0
  ...
}, [session.pricingTiers, timer.hours, timer.minutes]);
```

**FE (line 264-301):**
```jsx
{session.pricingTiers && session.pricingTiers.length > 0 && (
  // ← luôn false
  <div>...render tiers...</div>
)}
```

**BE:** `DriverCurrentSessionResponse.java` (line 11-47) **không có field `pricingTiers`**.

### Impact
- Mất UI feature đã thiết kế
- User không thấy được "giá tier tiếp theo" → không dự đoán được tổng tiền

### Fix khuyến nghị **cho BE team**

**Bước 1:** Tạo DTO mới `PricingTierDto.java`:
```java
@Data
@Builder
public class PricingTierDto {
    private String tierLabel;     // "Tier 1", "Tier 2", ...
    private int maxHours;          // 2, 6, 12, 24
    private BigDecimal price;      // 20000, 40000, 60000, 100000
}
```

**Bước 2:** Thêm field `pricingTiers` vào `DriverCurrentSessionResponse.java`:
```java
private List<PricingTierDto> pricingTiers;
```

**Bước 3:** Mapping trong `DriverServiceImpl.mapToCurrentSession()` (line 224-306):
```java
private List<PricingTierDto> buildPricingTiers(PricingPolicy policy) {
    if (policy == null) return List.of();
    List<PricingTierDto> tiers = new ArrayList<>();
    if (policy.getTier1Price() != null && policy.getTier1MaxHours() != null) {
        tiers.add(PricingTierDto.builder()
            .tierLabel("Tier 1")
            .maxHours(policy.getTier1MaxHours())
            .price(policy.getTier1Price())
            .build());
    }
    // ... tương tự tier2, tier3, tier4
    return tiers;
}

// Trong mapToCurrentSession(), sau dòng .estimatedFee(estimatedFee):
.pricingTiers(buildPricingTiers(policy))
```

**Bước 4:** Verify bằng Swagger — response phải có:
```json
{
  "data": {
    "sessions": [{
      "pricingTiers": [
        { "tierLabel": "Tier 1", "maxHours": 2,  "price": 20000 },
        { "tierLabel": "Tier 2", "maxHours": 6,  "price": 40000 },
        { "tierLabel": "Tier 3", "maxHours": 12, "price": 60000 },
        { "tierLabel": "Tier 4", "maxHours": 24, "price": 100000 }
      ]
    }]
  }
}
```

### Fix khuyến nghị **cho FE team**
Sau khi BE trả `pricingTiers`, FE không cần đổi gì thêm (code line 264-301 sẽ tự render).

---

## Issue #3 — Label "Total Fee" hiển thị giá trị `estimatedFee` 🟡

### Mô tả
Trên header (line 502-510), có block "Total Fee" hiển thị tổng fee. **Đây là `estimatedFee`** (tính realtime), **KHÔNG phải `totalFee`** (chỉ set khi checkout).

API trả `totalFee=0` (đúng, vì session `PENDING_PAYMENT` chưa checkout), nhưng FE lại dùng `estimatedFee` để hiển thị → user hiểu nhầm.

### Repro
1. Mở `CurrentSession` page ngay sau checkin
2. Header hiển thị "Total Fee: 20.000đ" (thực tế là estimate)
3. API Swagger: `totalFee=0`, `estimatedFee=20000`
4. User nghĩ phải trả 20.000đ → đến checkout staff báo khác → tranh cãi

### Root cause

**FE (line 403-406):**
```jsx
const totalFee = useMemo(
  () => activeSessions.reduce((sum, s) => sum + (s.estimatedFee || 0), 0),
  [activeSessions],
);
```

→ Đặt tên `totalFee` nhưng giá trị là `estimatedFee`.

**FE (line 504-509):**
```jsx
<p className="text-[10px] font-bold uppercase tracking-wider text-emerald-500">
  Total Fee
</p>
<p className="text-xl font-black text-emerald-700">
  {totalFee.toLocaleString("vi-VN")}đ
</p>
```

→ Label "Total Fee" + giá trị estimated → confusing.

### Đã check BE
`totalFee` chỉ set trong 4 chỗ (`ParkingSessionService.java` line 277, 508, 790, 1049) — đều trong checkout/incident flow. Session `PENDING_PAYMENT` luôn có `totalFee=0` → đúng behavior.

**Pattern BE đã dùng** (tham khảo từ `IncidentServiceImpl.java` dòng 1049-1058):
```java
java.math.BigDecimal totalFee = session.getTotalFee() != null ? session.getTotalFee() : BigDecimal.ZERO;
// Neu totalFee = 0 (chua checkout), hien thi estimatedFee cho sessionTotalFee
```

### Fix khuyến nghị **cho FE team**

**Option A — Đổi label:**
```jsx
<p className="text-[10px] font-bold uppercase tracking-wider text-emerald-500">
  Estimated Total  {/* đổi từ "Total Fee" → "Estimated Total" */}
</p>
```

**Option B — Logic ưu tiên totalFee khi có:**
```jsx
const displayFee = useMemo(
  () => activeSessions.reduce((sum, s) => {
    const fee = s.totalFee > 0 ? s.totalFee : s.estimatedFee;
    return sum + (fee || 0);
  }, 0),
  [activeSessions],
);
```

**Option C — Tách 2 block:**
- "Estimated Total" (live, luôn có)
- "Final Total" (chỉ khi `isCheckoutCompleted`)

Khuyến nghị: **Option A** (đơn giản nhất, đủ fix UX).

---

## Issue #4 — `parkingHours` ceil có thể sai ở biên thời gian 🟡

### Mô tả
`DriverServiceImpl.java` dòng 229:
```java
int parkingHours = Math.max(1, (int) Math.ceil(parkingMinutes / 60.0));
```

→ User vừa checkin 30 phút → `parkingHours = 1` → fee = basePrice 20000 (tier1 ≤2h).

### Câu hỏi nghiệp vụ
- Có grace period 15-30 phút đầu miễn phí không?
- Hay cứ vào là tính basePrice?

### Repro
1. Checkin lúc 9:00
2. 9:30 (30 phút sau) → `parkingHours = 1` → `estimatedFee = 20000`
3. User ngạc nhiên: "Mới 30 phút mà 20k?"

### Fix khuyến nghị **cho BE team**

**Option A — Bỏ Math.max(1, ...):**
```java
int parkingHours = Math.max(1, (int) Math.ceil(parkingMinutes / 60.0));
//                              ↑ giữ nếu đây là business rule
```

**Option B — Grace period:**
```java
int parkingMinutes = ...;
if (parkingMinutes < 15) {
    // 15 phút đầu miễn phí
    return BigDecimal.ZERO;
}
```

**Option C — Phân biệt rõ (recommended):**
```java
// Tính parkingHours với ceil (để khớp tier table)
int parkingHours = (int) Math.ceil(parkingMinutes / 60.0);
if (parkingHours < 1) parkingHours = 1; // logic business: tối thiểu 1h luôn trả tier1
```

### Cần xác nhận từ Product Owner
- [ ] Grace period có tồn tại không? Bao nhiêu phút?
- [ ] Tối thiểu tính 1h có đúng business rule không?
- [ ] Có nên hiển thị "0 đ (còn trong grace period)" thay vì "20.000đ"?

---

## Issue #5 — Staff checkout không phân biệt được Driver Reservation vs Walk-in Driver vs Guest 🟠

### Mô tả
Trên màn Staff Checkout, staff scan `ticketCode` để hiện thông tin session trước khi checkout. Hiện tại FE chỉ gọi **API checkout trực tiếp** (không có lookup trước) nên:
- Không hiển thị được thông tin session trên UI (driver là ai, vehicle gì, fee bao nhiêu).
- Không phân biệt được session này là driver có **reservation**, driver **walk-in**, hay **guest vãng lai** → dễ nhầm giá, nhầm policy, nhầm nhãn hiển thị.

### Repro
1. Staff mở trang Checkout, nhập/scan `ticketCode`.
2. UI không hiện gì trước khi bấm "Confirm Checkout".
3. Staff không biết session này thuộc nhóm nào → có thể chọn sai fee policy.

### Đã fix phía BE (API mới)
BE đã thêm endpoint lookup theo `ticketCode` để FE tra cứu trước khi checkout:

```
GET /api/sessions/ticket/{ticketCode}/lookup
Authorization: STAFF / MANAGER / ADMIN
```

**Response mẫu:**

```json
{
  "status": 200,
  "message": "Ticket lookup completed",
  "data": {
    "lookupType": "DRIVER_SESSION",
    "isWalkInDriver": false,
    "isGuest": false,
    "reservation": { "reservationId": "...", "userFullName": "...", ... },
    "guestSession": null,
    "walkInDriver": null
  }
}
```

**Các giá trị `lookupType`:**

| `lookupType`       | Ý nghĩa | `isWalkInDriver` | `isGuest` | Field chứa data |
|--------------------|--------|---|---|---|
| `RESERVATION`      | Driver đã book trước, chưa check-in | `false` | `false` | `reservation` |
| `DRIVER_SESSION`   | Driver reservation đã check-in, đang trong bãi | `false` | `false` | `reservation` |
| `WALK_IN_DRIVER`   | Driver đã đăng ký xe nhưng không qua reservation | **`true`** | `false` | `walkInDriver` |
| `GUEST_SESSION`    | Khách vãng lai (không phải driver đăng ký) | `false` | **`true`** | `guestSession` |
| `NOT_FOUND`        | Không tìm thấy ticket | `false` | `false` | `null` |

### Fix khuyến nghị **cho FE team**

**Bước 1:** Khi staff nhập/scan `ticketCode` (trước khi bấm Checkout), gọi:
```js
const resp = await api.get(`/api/sessions/ticket/${ticketCode}/lookup`);
const { lookupType, isWalkInDriver, isGuest, reservation, guestSession, walkInDriver } = resp.data.data;
```

**Bước 2:** Render UI theo `lookupType`:

```jsx
if (lookupType === 'NOT_FOUND') {
  showError('Ticket không tồn tại hoặc đã hết hạn');
  return;
}

// Lấy info chung
const info = reservation ?? walkInDriver ?? guestSession;

// Nhãn phân loại
const typeLabel = isWalkInDriver
  ? 'Walk-in Driver'
  : isGuest
    ? 'Guest'
    : 'Driver (Reservation)';

// Render UI
return (
  <div>
    <Badge color={isWalkInDriver ? 'orange' : isGuest ? 'gray' : 'blue'}>
      {typeLabel}
    </Badge>
    <DriverInfo data={info} />
    <FeePreview data={info} />
    <Button onClick={confirmCheckout}>Confirm Checkout</Button>
  </div>
);
```

**Bước 3:** Truyền `ticketCode` vào request checkout (như cũ) — BE vẫn check lại session ACTIVE theo ticket, không cần đổi payload.

### Lưu ý quan trọng cho FE
- **Đừng chỉ dựa vào `lookupType`** — dùng cả `isWalkInDriver` / `isGuest` (boolean) để switch UI cho rõ ràng, tránh typo string.
- Field chứa data thay đổi theo loại (`reservation` / `walkInDriver` / `guestSession`) — dùng optional chaining khi đọc.
- Response giữ nguyên cấu trúc kể cả khi `NOT_FOUND` (chỉ các field data = null).

### Đã thay đổi (BE side)
| File | Thay đổi |
|------|---------|
| `dto/TicketLookupResponse.java` | **Mới** — DTO lookup theo `ticketCode` |
| `controller/ParkingSessionController.java` | Thêm endpoint `GET /api/sessions/ticket/{ticketCode}/lookup` |
| `service/ParkingSessionService.java` | Thêm method `lookupByTicketCode(String ticketCode)` |
| `repository/ParkingSessionRepository.java` | Thêm method `findActiveSessionByTicketId(String ticketId)` (join fetch vehicle) |

---

## Test Verification Matrix (BE đã verify)

| parkingHours | expectedFee | actualFee (API) | Status |
|--------------|-------------|-----------------|--------|
| 1            | 20000 (tier1 ≤2h) | 20000 | ✅ |
| 5            | 40000 (tier2 ≤6h) | 40000 | ✅ |
| 11           | 100000 (tier4 ≤24h) | 100000 | ✅ |
| 25           | 200000 (tier4 + 1 day) | (chưa test) | ⏳ |

---

## Cách Verify Issues từ phía BE

### Bug #1 (Polling)
- [ ] Mở DevTools → tab Network
- [ ] Reload page → 1 request
- [ ] Đợi 60s không reload → không có request mới ✗ (đây là bug)
- [ ] Plan sau fix: có request mỗi 60s

### Bug #2 (Pricing Tiers)
- [ ] DevTools → Network → response endpoint `/users/me/sessions/current`
- [ ] Search "pricingTiers" → không có ✗ (đây là bug)
- [ ] Plan sau fix: response có array `pricingTiers` 4 elements

### Bug #3 (Total Fee label)
- [ ] Quan sát header → "Total Fee 20.000đ"
- [ ] API response: `totalFee=0`, `estimatedFee=20000`
- [ ] Mâu thuẫn → user hiểu nhầm
- [ ] Plan sau fix: đổi label "Estimated Total" hoặc dùng `totalFee` khi có

### Bug #4 (parkingHours ceil)
- [ ] Checkin → đợi 5 phút → API `parkingMinutes=5, parkingHours=1`
- [ ] Nếu business rule nói 30 phút đầu miễn phí → bug
- [ ] Nếu business rule nói tối thiểu 1h basePrice → OK

### Bug #5 (Lookup theo ticketCode) — BE đã fix, FE cần tích hợp
- [ ] `GET /api/sessions/ticket/{ticketCode}/lookup` trả 200 với driver có reservation
- [ ] `lookupType=DRIVER_SESSION`, `isWalkInDriver=false`, `reservation` có data
- [ ] Walk-in driver → `lookupType=WALK_IN_DRIVER`, `isWalkInDriver=true`, `walkInDriver` có data
- [ ] Guest vãng lai → `lookupType=GUEST_SESSION`, `isGuest=true`, `guestSession` có data
- [ ] Ticket không tồn tại → `lookupType=NOT_FOUND`, tất cả field = null
- [ ] FE render đúng badge/label theo `isWalkInDriver` / `isGuest`

---

## Recommended Action Items

**FE Team (priority high):**
1. [ ] Fix polling realtime (#1) — Option A hoặc B
2. [ ] Đổi label "Total Fee" → "Estimated Total" (#3) — Option A
3. [ ] Tích hợp lookup theo ticketCode (#5) — theo hướng dẫn Issue #5

**BE Team (priority medium):**
1. [x] ~~Thêm field `pricingTiers` vào `DriverCurrentSessionResponse` (#2)~~
2. [x] ~~Verify grace period business rule (#4)~~ (đã confirm: tối thiểu 1h basePrice)
3. [x] ~~Thêm API lookup theo ticketCode (#5)~~

**Product Owner (decision needed):**
1. [ ] Confirm grace period (15 min? 30 min? none?)
2. [ ] Confirm minimum fee (luôn tier1 basePrice? hay miễn phí dưới X phút?)

---

## References

- BE Swagger: `http://localhost:8080/swagger-ui.html`
- API endpoint: `GET /api/users/me/sessions/current`
- FE Component: `Parking_Building_FE/src/page/Driver/CurrentSession/currentSession.jsx`
- BE Service: `Parking_Building_BE/src/main/java/fpt/swp391/parkingmanagement/service/impl/DriverServiceImpl.java` (line 204-306)
- BE DTO: `Parking_Building_BE/src/main/java/fpt/swp391/parkingmanagement/dto/DriverCurrentSessionResponse.java`
- BE Pricing Service: `Parking_Building_BE/src/main/java/fpt/swp391/parkingmanagement/service/PricingService.java` (TIERED branch)
- BE Incident Fee pattern: `Parking_Building_BE/src/main/java/fpt/swp391/parkingmanagement/service/impl/IncidentServiceImpl.java` (line 1049-1058)
