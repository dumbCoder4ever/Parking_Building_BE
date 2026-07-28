# FE Fix — Admin Revenue PayOS (chỉ thấy giao dịch 18–22)

> Màn: Admin Dashboard → Revenue / PayOS  
> Báo lỗi: PayOS chỉ lấy được giao dịch từ ngày **18–22**, thiếu data cũ hơn (tháng 6, đầu tháng 7)  
> Ngày tổng hợp: 2026-07-25

---

## 1. Tóm tắt

| | |
|---|---|
| **Kết luận** | API dashboard **không** bị filter cứng ngày 18–22 |
| **Nguyên nhân chính** | FE gọi `GET /api/payments?limit=50` rồi lọc PayOS client-side → 50 bản ghi mới nhất đa số là **CASH**, PayOS cũ bị loại |
| **Đã sửa BE** | Thêm filter `paymentMethod`, `from`, `to`, `page` cho `/api/payments`; đồng bộ `revenueByPaymentMethod` theo date range; thêm `revenueTrendByPaymentMethod` |
| **Việc FE cần làm** | Đổi cách gọi API theo mục 5 — **không** lọc PayOS từ list 50 payment mixed |

---

## 2. Bối cảnh & triệu chứng

FE báo:

1. *"Coi lại chỗ revenue của admin"*
2. *"Sao cái PayOS nó lấy mấy cái giao dịch từ 18 đến 22 thôi vậy?"*
3. *"API lỗi à?"*

Kỳ vọng đúng: khi xem PayOS phải thấy **toàn bộ** giao dịch đã thanh toán, kể cả **trước** 18/7 (tháng 6, đầu tháng 7).

---

## 3. Dữ liệu thực tế trong DB (production)

Query ngày 2026-07-25:

| Metric | Giá trị |
|--------|---------|
| PayOS **PAID** all-time | **36 giao dịch**, **375.000đ** |
| PayOS **PAID** 7 ngày gần nhất (19–25/7) | **0** |
| PayOS **PAID** cuối cùng | **18/7** (2 giao dịch) |
| PayOS **PENDING** 22/7 | 1 giao dịch 74.000đ (không tính revenue) |
| CASH 7 ngày gần nhất | Nhiều giao dịch mỗi ngày 19–25/7 |

PayOS PAID theo ngày (một phần):

| Ngày | Số giao dịch PAID |
|------|-------------------|
| 2026-06-23 ~ 2026-06-30 | 10 |
| 2026-07-01 ~ 2026-07-16 | 24 |
| 2026-07-18 | 2 |
| 2026-07-19 ~ 2026-07-25 | 0 |

**Lưu ý:** Default dashboard `fromDay`/`toDay` = **7 ngày gần nhất** → trong khoảng đó **không có PayOS PAID**. Đây là data thật, không phải bug filter.

---

## 4. Phân tích nguyên nhân

### 4.1. Sai cách — lấy 50 payment mới nhất rồi lọc PayOS

**API cũ:**

```
GET /api/payments?status=PAID&limit=50
```

Logic BE: 50 payment **mới nhất** (all methods), sort `createdAt DESC`.

Simulate trên DB:

| paymentMethod | Ngày | count trong top 50 |
|---------------|------|--------------------|
| CASH | 17/7 – 25/7 | 48 |
| PAYOS | 18/7 | **2** |

→ FE lọc PayOS client-side chỉ thấy **2 giao dịch ngày 18/7**, mất **34 giao dịch** còn lại (tháng 6 – đầu tháng 7).

### 4.2. Dashboard stats — hành vi trước khi sửa

Endpoint admin:

```
GET /api/admin/dashboard/stats?fromDay=&toDay=
```

| Field | Hành vi cũ | Hành vi mới (sau fix) |
|-------|------------|------------------------|
| `revenueByPaymentMethod` | All-time (bỏ qua `fromDay`/`toDay`) | Theo `fromDay`/`toDay` |
| `revenueTrend` | Tổng tất cả method theo ngày | Không đổi |
| `revenueTrendByPaymentMethod` | **Không có** | **Mới** — trend tách theo CASH/PAYOS/VNPAY/... |

### 4.3. Không phải lỗi PayOS webhook

PayOS webhook / `confirmPaymentSuccess` vẫn set `paymentTime` khi PAID. Giao dịch cũ vẫn nằm trong DB — vấn đề là **cách query**, không mất data.

---

## 5. Hướng dẫn FE — API đúng

### 5.1. Danh sách giao dịch PayOS (kể cả trước 18/7)

```
GET /api/payments?paymentMethod=PAYOS&status=PAID
Authorization: Bearer <ADMIN_TOKEN>
Role: STAFF | MANAGER | ADMIN
```

| Param | Bắt buộc | Ghi chú |
|-------|----------|---------|
| `paymentMethod` | Khuyến nghị | `PAYOS`, `CASH`, `VNPAY`, `MOMO` |
| `status` | Khuyến nghị | `PAID` / `UNPAID` / `ALL` |
| `from` | ❌ | ISO-8601, lọc theo `paymentTime` (fallback `createdAt`) |
| `to` | ❌ | ISO-8601 |
| `page` | ❌ | Default `0` |
| `limit` | ❌ | Default `50`, max `500` |

**Ví dụ lấy toàn bộ PayOS PAID (36 giao dịch):**

```
GET /api/payments?paymentMethod=PAYOS&status=PAID&limit=100
```

**Ví dụ lấy PayOS trong tháng 6:**

```
GET /api/payments?paymentMethod=PAYOS&status=PAID&from=2026-06-01T00:00:00&to=2026-06-30T23:59:59
```

### 5.2. Dashboard revenue — Admin

```
GET /api/admin/dashboard/stats?fromDay=2026-06-01&toDay=2026-07-25
Authorization: Bearer <ADMIN_TOKEN>
```

Response liên quan revenue:

```json
{
  "revenueByPaymentMethod": [
    { "method": "CASH", "totalRevenue": 1234567, "count": 64 },
    { "method": "PAYOS", "totalRevenue": 375000, "count": 36 }
  ],
  "revenueTrend": [
    { "date": "2026-06-23", "revenue": 40000, "count": 2 }
  ],
  "revenueTrendByPaymentMethod": [
    { "date": "2026-06-23", "method": "PAYOS", "revenue": 40000, "count": 2 },
    { "date": "2026-06-24", "method": "PAYOS", "revenue": 30000, "count": 3 }
  ]
}
```

| Field | Dùng cho |
|-------|----------|
| `revenueByPaymentMethod` | Card tổng theo method trong khoảng `fromDay`–`toDay` |
| `revenueTrend` | Chart tổng revenue (all methods) |
| `revenueTrendByPaymentMethod` | Chart/filter theo từng method — **dùng cho tab PayOS** |

**Lọc PayOS trên FE:**

```javascript
const payosTrend = data.revenueTrendByPaymentMethod.filter(
  (item) => item.method?.toUpperCase() === 'PAYOS'
);
```

### 5.3. Manager dashboard (nếu dùng chung component)

```
GET /api/manager/dashboard/stats?fromDay=&toDay=&buildingId=
```

Cùng shape `DashboardStatsResponse`, có thêm filter `buildingId`.

### 5.4. Revenue theo building

```
GET /api/manager/dashboard/revenue?from=&to=&buildingId=
```

Chỉ tổng revenue + breakdown theo building, **không** tách payment method.

---

## 6. ❌ Sai vs ✅ Đúng

### ❌ Sai — hay gặp

```javascript
// SAI: lấy 50 payment mới nhất rồi lọc PayOS
const { data } = await api.get('/api/payments?status=PAID&limit=50');
const payos = data.data.filter((p) => p.paymentMethod === 'PAYOS');

// SAI: dùng revenueTrend (tổng all methods) cho tab PayOS
setChart(data.revenueTrend);

// SAI: chỉ truyền toDay=22, fromDay=18 mà kỳ vọng thấy cả tháng 6
api.get('/api/admin/dashboard/stats?fromDay=2026-07-18&toDay=2026-07-22');
```

### ✅ Đúng

```javascript
// Danh sách chi tiết PayOS
const { data: payments } = await api.get('/api/payments', {
  params: { paymentMethod: 'PAYOS', status: 'PAID', limit: 100 },
});

// Dashboard chart PayOS theo ngày
const { data: stats } = await api.get('/api/admin/dashboard/stats', {
  params: { fromDay: '2026-06-01', toDay: '2026-07-25' },
});
const payosTrend = stats.data.revenueTrendByPaymentMethod.filter(
  (x) => x.method === 'PAYOS'
);
const payosTotal = stats.data.revenueByPaymentMethod.find(
  (x) => x.method === 'PAYOS'
);
```

---

## 7. Thay đổi Backend (đã merge)

### 7.1. `GET /api/payments`

File: `PaymentController.java`, `PaymentService.java`, `PaymentRepository.java`

- Thêm query `findForStaffList` với filter `paymentMethod`, `from`, `to`
- Sort theo `COALESCE(paymentTime, createdAt) DESC`
- `status=ALL` hoặc không truyền → không lọc status

### 7.2. `GET /api/admin/dashboard/stats`

File: `DashboardStatsService.java`, `DashboardStatsResponse.java`

- `revenueByPaymentMethod` dùng cùng `fromDay`/`toDay` với `revenueTrend`
- Thêm field `revenueTrendByPaymentMethod` (DTO: `RevenueTrendByMethodItem`)
- Query native: `getRevenueTrendByPaymentMethod` group by `date + payment_method`

### 7.3. File liên quan

| File | Thay đổi |
|------|----------|
| `repository/PaymentRepository.java` | `findForStaffList`, `getRevenueTrendByPaymentMethod` |
| `repository/RevenueTrendByMethodProjection.java` | Projection mới |
| `dto/RevenueTrendByMethodItem.java` | DTO mới |
| `dto/DashboardStatsResponse.java` | Field `revenueTrendByPaymentMethod` |
| `service/DashboardStatsService.java` | Đồng bộ date range + build trend by method |
| `service/PaymentService.java` | Filter params cho staff list |
| `controller/PaymentController.java` | Query params mới |
| `controller/AdminDashboardController.java` | Cập nhật Swagger description |

---

## 8. Checklist FE

- [ ] Tab/list PayOS: gọi `/api/payments?paymentMethod=PAYOS&status=PAID` — **không** lọc từ list mixed
- [ ] Chart PayOS: dùng `revenueTrendByPaymentMethod`, không dùng `revenueTrend`
- [ ] Date picker admin revenue: truyền `fromDay`/`toDay` đủ rộng (vd. từ đầu tháng hoặc 30 ngày) nếu muốn thấy PayOS cũ
- [ ] Card tổng PayOS: lấy từ `revenueByPaymentMethod` cùng khoảng ngày với chart
- [ ] Không tính `PENDING` vào revenue (BE chỉ count `PAID` / `CONFIRMED` / `SUCCESS`)
- [ ] Default 7 ngày gần nhất → PayOS có thể = 0 trên chart (data thật từ 19/7)

---

## 9. FAQ

**Q: PayOS all-time có 36 giao dịch, sao chart 7 ngày gần nhất trống?**  
A: PayOS PAID cuối là 18/7. Khoảng 19–25/7 không có PayOS PAID. Mở rộng `fromDay` về trước (vd. `2026-06-01`).

**Q: Có cần endpoint riêng `/api/admin/payments/payos` không?**  
A: Không bắt buộc. `GET /api/payments?paymentMethod=PAYOS` đủ dùng.

**Q: `revenueTrend` và `revenueTrendByPaymentMethod` khác gì?**  
A: `revenueTrend` = tổng mọi method. `revenueTrendByPaymentMethod` = tách từng method — dùng cho tab PayOS/CASH/VNPAY.

**Q: Cache dashboard?**  
A: Có — `dashboardStats` cache ~30s (Caffeine). Sau deploy/fix, đợi tối đa 30s hoặc đổi query param để bust cache key.

---

## 10. Tham chiếu endpoint

| Endpoint | Role | Mục đích |
|----------|------|----------|
| `GET /api/admin/dashboard/stats` | ADMIN | Stats + revenue by method + trend |
| `GET /api/manager/dashboard/stats` | MANAGER, ADMIN | Giống admin, có `buildingId` |
| `GET /api/manager/dashboard/revenue` | MANAGER, ADMIN | Revenue theo building |
| `GET /api/payments` | STAFF, MANAGER, ADMIN | Danh sách payment có filter |

Tài liệu payment gateway setup: `docs/payment-setup.md`
