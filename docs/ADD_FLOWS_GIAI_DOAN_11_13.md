# Add Flows — GIAI ĐOẠN 11→13

## GIAI ĐOẠN 11 — SCHEDULER
**(ĐÃ CÓ)**

| Job | Class | Interval | Ghi chú |
|---|---|---|---|
| Reservation hết hạn | `AutoCancelReservationJob` | 60s | Đã có sẵn |
| Giải phóng Slot | `SlotStatusSyncJob` | 30s | Đã có sẵn |
| Vehicle Overstay | `VehicleOverstayJob` | 5m | Notify staff/driver + audit |
| Payment Reminder | `PaymentReminderJob` | 5m | Session `PENDING_PAYMENT` |
| Peak Hour Notification | `PeakHourNotificationJob` | 10m | 1 lần / building / giờ |
| Maintenance Notification | `MaintenanceNotificationJob` | 1h | Reminder hạng mục MAINTENANCE |

Events WS: `VEHICLE_OVERSTAY`, `PAYMENT_REMINDER`, `PEAK_HOUR_ALERT`, `MAINTENANCE_REMINDER`

---

## GIAI ĐOẠN 12 — SYSTEM CONFIGURATION
**(ĐÃ CÓ)**

- Bảng `system_configs` — SQL: `src/main/resources/db/create_system_configs_table.sql`
- API:
  - `GET /api/manager/system-configs`
  - `GET /api/manager/system-configs/{configKey}`
  - `PUT /api/manager/system-configs/{configKey}`

| Key | Default | Dùng bởi |
|---|---|---|
| `GRACE_PERIOD_MINUTES` | 15 | Tạo Reservation |
| `PAYMENT_REMINDER_MINUTES` | 15 | PaymentReminderJob |
| `MAX_PARKING_HOURS` | 24 | VehicleOverstayJob (rule `MAX_PARKING_HOURS` override theo building) |
| `PEAK_HOUR_STDDEV_FACTOR` | 1.0 | PeakHourService threshold |
| `OVERSTAY_NOTIFY_ENABLED` | true | Bật/tắt overstay job |

---

## GIAI ĐOẠN 13 — REPORT EXPORT
**(ĐÃ CÓ — Driver Report placeholder)**

- `GET /api/manager/reports/export?reportType=&format=&buildingId=&fromDay=&toDay=`
- `reportType`: `REVENUE` \| `INCIDENT` \| `OCCUPANCY` \| `PEAK_HOUR` \| `DRIVER_REPORT`
- `format`: `EXCEL` \| `PDF`
- `DRIVER_REPORT`: entity chưa có → file ghi chú placeholder
