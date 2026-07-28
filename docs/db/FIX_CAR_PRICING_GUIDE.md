# Fix: Car Pricing Policy trả sai fee (20000 thay vì 60000 cho parkingHours=12)

## Vấn đề

API `GET /api/users/me/sessions/current` trả về:
```json
{
  "vehicleTypeName": "Car",
  "parkingHours": 12,
  "estimatedFee": 20000   // ❌ SAI - phải là 60000 (tier3)
}
```

Root cause: **DB đang dùng policy `HOURLY` cho Car**, không phải `TIERED`:
```sql
-- File parking_db.sql / init.sql seed policy Car:
pricing_type = 'HOURLY'
base_price = 20000
hourly_rate = 10000  -- (hoặc 0 tùy env)
```

→ Code chạy nhánh HOURLY: `20000 + hourlyRate * 11` → không khớp với logic TIERED mà tôi (Cursor) đã fix ở `calculateTiered()`.

## Log kiểm chứng

Đã thêm log vào `PricingService.calculateTiered()`:
```
[PRICING][TIERED] ENTRY hours=12, tier1=2h/20000d, tier2=6h/40000d, tier3=12h/60000d, tier4=24h/100000d
[PRICING][TIERED] MATCH tier3 (hours=12, limit=12) -> price=60000
```

Nếu log **không xuất hiện** → code không chạy nhánh TIERED → DB đang HOURLY → cần fix DB.

## Cách fix

### ✅ Recommended: Run SQL `fix_car_pricing_to_tiered.sql`

```bash
mysql -h <host> -u <user> -p <db_name> < fix_car_pricing_to_tiered.sql
```

**Trên Railway:**
1. Vào project Railway → tab MySQL
2. Click "Data" → tab "Query"
3. Paste nội dung file `fix_car_pricing_to_tiered.sql` (trừ phần `START TRANSACTION/COMMIT` vì Railway UI tự quản lý)
4. Execute

Hoặc dùng Railway CLI:
```bash
railway run mysql -h $MYSQL_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE < fix_car_pricing_to_tiered.sql
```

### Sau khi fix DB

1. **Restart BE service** (để clear `@Cacheable("pricingPolicies")` cache)
   ```bash
   # Railway tab Deployments → click "Restart"
   ```
2. **Đợi 30 giây** cho service ready
3. **Test lại API:**
   ```bash
   curl http://localhost:8080/api/users/me/sessions/current
   ```

## Expected output sau fix

| vehicleTypeName | parkingHours | estimatedFee (expected) |
|-----------------|--------------|------------------------|
| Car             | 1            | 20000 (tier1 ≤2) |
| Car             | 5            | 40000 (tier2 ≤6) |
| **Car**         | **12**       | **60000 (tier3 ≤12)** ← FIX TARGET |
| Car             | 13           | 100000 (tier4 ≤24) |
| Motorbike       | 12           | 15000 (tier3 ≤12) |

## Alternative: Fix code HOURLY (nếu không muốn đổi DB)

Nếu business rule là **HOURLY cho Car** (tính theo giờ), thì logic cần fix là:
```java
// PricingService.java line 80-81
// HOURLY (default): basePrice + hourlyRate * (hours - 1)
return basePrice.add(hourlyRate.multiply(BigDecimal.valueOf(hours - 1)));
```

→ Vấn đề: `basePrice + hourlyRate*(hours-1)` chỉ đúng khi hourly_rate > 0. Nếu hourly_rate = 0 (như data hiện tại), giá trị bị "đóng băng" ở basePrice.

Nếu muốn giữ HOURLY, logic cần là:
```java
// Capping: tối đa = basePrice + hourlyRate * 23 (24h cap)
BigDecimal total = basePrice.add(hourlyRate.multiply(BigDecimal.valueOf(Math.min(hours, 24) - 1)));
```

## Khuyến nghị

**Chạy SQL fix + restart BE** là cách nhanh nhất và đúng với logic đã fix ở loop `calculateTiered()`.

Nếu sau khi fix vẫn sai → check log `[PRICING]` trong Railway logs để xác nhận code đã chạy nhánh TIERED.
