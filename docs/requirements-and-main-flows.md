# Parking Building Management System - Requirements & Main Flows

> Tài liệu mô tả yêu cầu & luồng chính của hệ thống quản lý bãi đỗ xe.  
> Cập nhật lần cuối: 2026-07-17

---

## 1. VAI TRÒ TRONG HỆ THỐNG

### 1.1 DRIVER / USER
Người gửi xe. Có thể:
- Đăng ký tài khoản
- Đăng nhập
- Xem slot trống
- Đặt trước slot
- Nhận ticket
- Gửi xe
- Thanh toán
- Lấy xe

### 1.2 PARKING STAFF
Nhân viên bãi xe. Nhiệm vụ:
- Kiểm tra ticket
- Xác minh thông tin xe
- Check-in xe
- Check-out xe
- Cập nhật trạng thái slot
- Xử lý sự cố

### 1.3 PARKING MANAGER
Quản lý bãi xe. Nhiệm vụ:
- Quản lý building
- Quản lý floor
- Quản lý zone
- Quản lý slot
- Xem occupancy
- Xem doanh thu
- Xem báo cáo

### 1.4 SYSTEM ADMIN
Quản trị hệ thống. Nhiệm vụ:
- Quản lý user
- Phân quyền
- Khóa tài khoản
- Quản lý hệ thống

---

## 2. CHI TIẾT CHỨC NĂNG THEO ROLE

### 2.1 Parking Manager
- Quản lý thông tin tòa nhà gửi xe
- Quản lý loại phương tiện
- Quản lý phân tầng theo loại xe
- Quản lý slot đỗ xe và trạng thái slot: theo dõi slot còn trống, đang sử dụng, đã đặt trước, bảo trì hoặc tạm khóa
- Quản lý bảng giá, quy định chính sách tính phí gửi xe
- Xem báo cáo lượt xe vào/ra, doanh thu, tỷ lệ lấp đầy, khung giờ cao điểm theo từng loại phương tiện
- Các quản lý nâng cao khác: theo dõi các trường hợp mất vé, sai biển số, quá giờ, gửi sai khu vực, xe chưa thanh toán (optional)

### 2.2 Parking Staff
- Hỗ trợ xử lý xe vào bãi: kiểm tra điều kiện xe vào bãi, nhập/quét biển số xe, hướng dẫn xe vào đúng tầng/khu vực theo loại phương tiện
- Tạo lượt gửi xe (parking session) cho xe gửi theo lượt, ghi nhận thời gian vào, loại xe, cổng vào
- Hỗ trợ xử lý xe ra bãi: tìm lượt gửi xe, xác nhận thời gian ra, kiểm tra phí cần thanh toán, thu phí gửi xe
- Hỗ trợ xử lý các trường hợp ngoại lệ: mất thẻ xe, sai thông tin xe, xe quá hạn gửi, xe gửi sai khu vực, cập nhật trạng thái slot

### 2.3 Parking User / Driver
- Xem thông tin bãi xe: thời gian hoạt động, loại xe được phục vụ, bảng giá và quy định gửi xe, số slot trống
- Gửi xe theo lượt: nhận thẻ xe/mã gửi xe khi vào bãi và thanh toán phí khi ra
- Đặt chỗ trước: đặt chỗ theo loại phương tiện, thời gian gửi và khu vực còn trống (nếu hệ thống hỗ trợ)
- Theo dõi lượt gửi xe: xem thông tin lượt gửi xe hiện tại: giờ vào, loại xe, khu vực gửi, phí tạm tính
- Thanh toán phí gửi xe và dịch vụ bổ sung nếu có
- Gửi phản hồi về mất thẻ xe, sai phí, khó tìm xe, slot bị chiếm hoặc vấn đề trong bãi xe (optional)

### 2.4 System Administrator
- Quản lý tài khoản người dùng
- Phân quyền
- Quản lý cấu hình hệ thống

---

## 3. AI / SMART SLOT (OPTIONAL / RESEARCH)
Hệ thống có thể (khuyến khích):
- Tự động chọn slot gần nhất
- Tối ưu occupancy
- Giảm thời gian tìm chỗ
- Ví dụ: Car nhỏ → Floor 2, Car VIP → Floor 3

Mục tiêu: tối ưu phân bổ chỗ đỗ xe theo loại phương tiện trong tòa nhà gửi xe sao cho giảm thời gian tìm chỗ, tăng tỷ lệ sử dụng bãi xe.

---

## 4. CORE FLOW CHÍNH

### FLOW 1 — USER ĐẶT TRƯỚC SLOT
1. **Login**: User đăng nhập.
2. **Xem slot còn trống**: Hệ thống hiển thị loại xe, zone, floor, slot available.
   - Ví dụ: Floor 1 → Motorbike → 20 slots; Floor 2 → Car → 5 slots.
3. **Nhập thông tin xe**: biển số, màu xe, hãng xe, model, loại xe.
4. **Chọn thời gian gửi**: ví dụ 14:00 → 18:00.
5. **Tạo Reservation**: tạo reservation, gán slot phù hợp, slot: AVAILABLE → RESERVED.
6. **Sinh Ticket**: tạo ticket_code + qr_code cho user.

### FLOW 2 — STAFF XÁC NHẬN XE VÀO BÃI
1. User đến bãi, đưa ticket/QR/biển số.
2. Staff có 2 cách tìm reservation:
   - **Cách 1**: Quét/ nhập ticket code trực tiếp.
   - **Cách 2**: Quét OCR biển số xe → gọi API `/api/staff/reservations/by-plate?plateNumber=XXX` → hệ thống tự tìm reservation PENDING/APPROVED của driver.
3. Staff đối chiếu ticket, biển số, loại xe, màu xe.
4. Nếu hợp lệ: tạo Parking Session, slot RESERVED → OCCUPIED, ghi checkin_time, checkin_vehicle_image, slot, vehicle, staff.
5. Nếu không hợp lệ: sai biển số / sai loại xe / quá giờ reservation → staff từ chối check-in.

### FLOW 3 — AUTO CANCEL RESERVATION
- User đặt 14:00, đến 14:30 vẫn chưa tới.
- Sau grace period (mặc định 15 phút, config `parking.grace-period-minutes`): reservation bị hủy, slot RESERVED → AVAILABLE.

### FLOW 4 — USER LẤY XE RA
1. User đưa ticket, staff tìm Parking Session.
2. Kiểm tra session, vehicle, slot, thời gian gửi.
3. Tính phí theo pricing policy: loại xe, số giờ, overtime, peak hour.
4. Thanh toán: Payment CASH hoặc BANKING (VNPay/PayOS/MOMO).
5. Kết thúc session: checkout_time, checkout_vehicle_image, total_fee, session_status = COMPLETED.
6. Giải phóng slot: OCCUPIED → AVAILABLE.

### FLOW 5 — MANAGER QUẢN LÝ BÃI XE
- **Building**: tên, giờ hoạt động, địa chỉ.
- **Floor**: Floor 1, Floor 2, …
- **Zone**: Motorbike / Car / VIP.
- **Slot**: AVAILABLE / RESERVED / OCCUPIED / MAINTENANCE.
- **Occupancy realtime**: ví dụ Floor 1: 80/100 occupied.
- **Báo cáo**: doanh thu, peak hour, tỷ lệ lấp đầy, lượt xe.

### FLOW 6 — INCIDENT FLOW
- Mất ticket, sai biển số, quá giờ, đỗ sai khu vực → tạo Incident.

---

## 7. IMAGE STORAGE

### Cloudinary Integration
Hệ thống sử dụng **Cloudinary** để lưu trữ hình ảnh check-in/checkout:

| Image Type | DB Column | Description |
|------------|-----------|-------------|
| Check-in Vehicle Image | `checkin_vehicle_image` | Ảnh xe lúc vào bãi |
| Checkout Vehicle Image | `checkout_vehicle_image` | Ảnh xe lúc ra bãi |

**Flow upload ảnh:**
1. Staff chụp ảnh xe khi vào/ra bãi
2. Ảnh được upload lên Cloudinary qua API `/api/sessions/checkin` hoặc `/api/sessions/checkout`
3. Cloudinary trả về URL, BE lưu vào DB (`checkin_vehicle_image` / `checkout_vehicle_image`)

**API Endpoints liên quan:**
- `POST /api/sessions/checkin` - Check-in kèm upload ảnh
- `POST /api/sessions/checkout` - Check-out kèm upload ảnh
- `GET /api/staff/reservations/by-plate?plateNumber=XXX` - Tìm reservation theo biển số (OCR)

---

## 5. LUỒNG DATABASE CHÍNH

```
User → Vehicle → Reservation → Ticket → Parking Session → Payment
Manager quản lý:
Building → Floor → Zone → Parking Slot
```

---

## 6. CÁC TRẠNG THÁI QUAN TRỌNG

### SLOT STATUS
- AVAILABLE
- RESERVED
- OCCUPIED
- MAINTENANCE

### RESERVATION STATUS
- PENDING
- CONFIRMED
- CANCELLED
- EXPIRED

### SESSION STATUS
- ACTIVE
- COMPLETED
- CANCELLED

### PAYMENT STATUS
- UNPAID
- PAID
- FAILED