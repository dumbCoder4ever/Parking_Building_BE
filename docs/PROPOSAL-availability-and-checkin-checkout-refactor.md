# Đề xuất sửa project Parking_Building_BE

> **Trạng thái**: ĐANG ĐỢI USER DUYỆT. Không có thay đổi nào được thực hiện cho tới khi anh/chị xác nhận.
>
> **Phạm vi**: Khôi phục endpoint `/buildings/{id}/floors` cho FE, hợp nhất `quick-checkin` và `checkin` thành một, tách `checkout` DRIVER (chỉ xác nhận xe ra, payment xử lý riêng) và GUEST (checkout kết hợp payment), thêm state `PENDING_PAYMENT` cho session, thêm ràng buộc chống checkin trùng biển số.

---

## Phần 1 — Báo cáo lỗi hiện tại của project (trước khi sửa)

### 1.1 Lỗi NGHIÊM TRỌNG — Endpoint Availability không khớp giữa FE và BE

FE Driver (`Parking_Building_FE/src/service/Driver/revervationApi.js`) đang gọi 3 endpoint:

| FE gọi | BE `BuildingController` hiện có | Hậu quả |
|---|---|---|
| `GET /api/buildings/available` | CÓ | OK |
| `GET /api/buildings/{id}/floors` | **KHÔNG CÓ** (đã xóa khi refactor) | **404 khi staff/driver bấm mở rộng building** |
| `GET /api/buildings/{id}/info` | **KHÔNG CÓ** | 404 (FE đã import nhưng chưa dùng) |

### 1.2 Bug logic — `quickGuestCheckin` không kiểm tra biển số đã ACTIVE

- `guestCheckin` (`ParkingSessionService.java:522–527`): CÓ check duplicate.
- `guestCheckinOcr` (`ParkingSessionService.java:745–753`): CÓ check duplicate.
- `quickGuestCheckin` (`ParkingSessionService.java:1229–1323`): **THIẾU check** → có thể tạo 2 session ACTIVE cùng biển số.

### 1.3 State machine `SessionStatus.PENDING_PAYMENT` chưa được dùng

Enum `SessionStatus` khai báo `PENDING_PAYMENT` và `CANCELLED` nhưng **không có code nào set session vào 2 trạng thái này** — dẫn đến việc flow driver hiện tại (checkin → payment sau → checkout) không có trạng thái trung gian để phân biệt "đã vào bãi nhưng chưa thanh toán".

### 1.4 Logic tạm thời trong `confirmExitAndCheckout`

`ParkingSessionService.java:396–423`: Set `sessionStatus = "ACTIVE"` ở giữa hàm rồi cuối hàm lại set `sessionStatus = "COMPLETED"`. Nếu exception xảy ra giữa chừng có thể để lại trạng thái không nhất quán.

---

## Phần 2 — Ý tưởng & kế hoạch sửa (theo yêu cầu của anh/chị)

### Mục A. Khôi phục `/api/buildings/{id}/floors` cho FE Driver

**Lý do chọn hướng này**: FE hiện tại đang gọi path này, sửa FE tốn công hơn sửa BE.

**Cách làm**:
- Thêm vào file `BuildingController.java` endpoint mới:
  ```
  GET /api/buildings/{buildingId}/floors
  → trả về List<FloorDto> gồm: floorId, floorNumber, buildingId, list zones (zoneId, zoneName, list slot summary)
  ```
- Role: `DRIVER`, `STAFF`, `MANAGER`, `ADMIN`.
- Bổ sung method `BuildingService.listFloorsOfBuilding(buildingId)` trả về floors + zones (không kèm slot grid chi tiết — chỉ summary count free/total).
- Giữ nguyên 2 endpoint cũ: `/buildings/available` và `/zones/{zoneId}/slots`.

### Mục B. Hợp nhất `quick-checkin` và `checkin` thành 1 endpoint `checkin` duy nhất

**Lý do**: Hai endpoint có logic gần giống nhau, gây trùng lặp. Hợp nhất sẽ:
- FE chỉ cần map 1 endpoint.
- BE giảm duplicate logic.
- Validation "chống checkin trùng biển số" được áp dụng đồng nhất.

**Cách làm**:
- **Xóa** controller `QuickSessionController` (mapping `/api/sessions/quick-checkin`).
- **Mở rộng** `POST /api/sessions/checkin` (ParkingSessionController) để nhận thêm 2 mode:
  - `mode = DRIVER`: cần OCR + ticket từ reservation (giống quickDriverCheckin hiện tại).
  - `mode = GUEST`: cần OCR + buildingId + vehicleTypeId (giống quickGuestCheckin hiện tại).
- Request DTO mới `UnifiedCheckinRequest`:
  ```
  plateImage: MultipartFile (bắt buộc - OCR)
  mode: CheckinMode (DRIVER | GUEST) — bắt buộc
  ticketCode: String (bắt buộc nếu mode=DRIVER)
  buildingId: String (bắt buộc nếu mode=GUEST)
  vehicleTypeId: String (bắt buộc nếu mode=GUEST)
  vehicleColor?, brand?, model?, guestName?, guestPhone?, note?
  ```
- Response DTO: `UnifiedCheckinResponse` (gộp 2 response hiện tại, có thêm trường `checkinMode`).

### Mục C. Tách `checkout` thành 2 endpoint riêng cho DRIVER và GUEST

**Lý do**:
- Driver: `checkin → payment → checkout`. Checkout chỉ xác nhận xe ra (không xử lý payment).
- Guest: `checkin → checkout → payment` (checkout kết hợp payment). Lý do: Guest là walk-in, staff cần thu tiền ngay khi checkout.

**Cách làm**:
- **`POST /api/sessions/driver/checkout`** (driver có reservation):
  - Input: `ticketCode`, `checkoutImage?` (optional).
  - Validate: `session.paymentStatus == PAID` (đã thanh toán qua VNPAY/PayOS từ trước).
  - Nếu chưa thanh toán → throw `PAYMENT_NOT_COMPLETED` (ép driver phải thanh toán trước).
  - Set `sessionStatus = COMPLETED`, slot → `AVAILABLE`, reservation → `COMPLETED`.
  - KHÔNG tạo Payment record trong endpoint này.
- **`POST /api/sessions/guest/checkout`** (giữ nguyên logic cũ + OCR):
  - Input: `ticketCode` (hoặc `plateImage` cho OCR mode), `paymentMethod?`, `checkoutImage?`.
  - Tính phí, phân nhánh payment:
    - CASH → tạo `Payment(PAID)` ngay, set session COMPLETED.
    - Electronic → yêu cầu `paymentStatus == PAID` (do webhook).
- **Xóa** endpoint cũ `/api/sessions/checkout` để tránh nhầm lẫn.

### Mục D. Thêm state `PENDING_PAYMENT` cho session

**Lý do**: Phân biệt rõ ràng "đã checkin nhưng chưa thanh toán".

**Cách làm**:
- Sau khi `checkin()` thành công (cả DRIVER & GUEST) → set `sessionStatus = "PENDING_PAYMENT"` thay vì `"ACTIVE"`.
- Sau khi payment thành công (webhook VNPay/PayOS) → set `sessionStatus = "ACTIVE"`.
- Khi staff gọi `driver/checkout` → validate `sessionStatus == "ACTIVE"` và `paymentStatus == "PAID"` → set `"COMPLETED"`.
- Enum `SessionStatus` đã có sẵn `PENDING_PAYMENT`, không cần thêm enum mới.
- Cập nhật các query JPQL trong `ParkingSessionRepository`:
  - `findByTicketTicketIdAndSessionStatus` → thay `"ACTIVE"` bằng `"PENDING_PAYMENT"` hoặc `"ACTIVE"` (tùy use case).
  - `findActiveGuestByPlateNumber` → đổi từ `"ACTIVE"` thành check cả `"PENDING_PAYMENT"` (vì session vừa checkin xong có thể ở PENDING_PAYMENT).

### Mục E. Ràng buộc "biển số đã checkin rồi thì không cho checkin nữa" (áp dụng cho cả DRIVER và GUEST)

**Cách làm**:
- Trong `UnifiedCheckinService.checkin()` (sau khi OCR xong biển số):
  - Với DRIVER: trước khi tạo session mới, check `findActiveSessionByPlate(plateNumber)` (bao gồm cả `PENDING_PAYMENT` và `ACTIVE`) → nếu tồn tại → throw `PLATE_ALREADY_PARKED`.
  - Với GUEST: làm tương tự (hiện `quickGuestCheckin` thiếu check này).
- Helper method: `validateNoActiveSessionForPlate(String plateNumber, String role)` — dùng chung cho cả 2 mode.

### Mục F. Sửa logic tạm thời trong `confirmExitAndCheckout`

**Cách làm**:
- Bỏ dòng `session.setSessionStatus("ACTIVE")` thừa ở giữa hàm (line 396).
- Hoặc nếu giữ confirmExitAndCheckout, đảm bảo flow: set `sessionStatus = PENDING_PAYMENT` (sau checkin) → payment → `ACTIVE` → confirmExit → `COMPLETED`.

---

## Phần 3 — Kế hoạch triển khai (sau khi anh/chị duyệt)

### Bước 1: Chuẩn bị (không breaking change)
1. Tạo mới `UnifiedCheckinRequest` và `UnifiedCheckinResponse` DTO.
2. Tạo `BuildingService.listFloorsOfBuilding(buildingId)` và `FloorDto`.
3. Thêm `/api/buildings/{id}/floors` vào `BuildingController`.

### Bước 2: Hợp nhất checkin
4. Viết `UnifiedCheckinService` (gộp `quickDriverCheckin` + `quickGuestCheckin` + `checkin`).
5. Thêm endpoint `/api/sessions/checkin` mới vào `ParkingSessionController` (giữ tên cũ, mở rộng input).
6. Thêm validation `validateNoActiveSessionForPlate`.
7. (Tạm thời) giữ endpoint `/api/sessions/quick-checkin` cũ nhưng mark `@Deprecated` để FE chuyển đổi dần.

### Bước 3: Tách checkout
8. Tạo `/api/sessions/driver/checkout` mới.
9. Tạo `/api/sessions/guest/checkout` (giữ logic cũ, nhưng đổi path).
10. Mark `@Deprecated` endpoint `/api/sessions/checkout` cũ.

### Bước 4: State machine PENDING_PAYMENT
11. Cập nhật `checkin()` → set `PENDING_PAYMENT` thay vì `ACTIVE`.
12. Cập nhật `paymentService.confirmPaymentSuccess()` → set session sang `ACTIVE`.
13. Cập nhật các query JPQL `findByXxxAndSessionStatus("ACTIVE")` → đổi thành `("ACTIVE", "PENDING_PAYMENT")` hoặc viết helper `findSessionInProgress()`.

### Bước 5: Dọn dẹp
14. Xóa `QuickSessionController` (sau khi FE chuyển sang dùng endpoint mới).
15. Xóa `/api/sessions/checkout` cũ (sau khi FE chuyển sang dùng 2 endpoint mới).
16. Xóa dead code trong `confirmExitAndCheckout`.

### Bước 6: Test end-to-end
17. Test driver flow: tạo reservation → checkin (PENDING_PAYMENT) → payment VNPAY → checkout (ACTIVE → COMPLETED).
18. Test guest flow: checkin (PENDING_PAYMENT) → checkout CASH (COMPLETED + Payment PAID) hoặc checkout electronic → payment → COMPLETED.
19. Test validation: checkin 2 lần cùng biển số GUEST → phải throw `PLATE_ALREADY_PARKED`.
20. Test FE: mở trang Availability → bấm mở rộng building → nhận đúng danh sách floors.

---

## Phần 4 — File sẽ bị ảnh hưởng (BE)

1. `src/main/java/.../controller/BuildingController.java` — thêm 1 endpoint.
2. `src/main/java/.../controller/ParkingSessionController.java` — thay thế /checkin, /checkout.
3. `src/main/java/.../controller/QuickSessionController.java` — xóa (sau khi FE migrate).
4. `src/main/java/.../controller/GuestSessionController.java` — đổi path /guest/checkout sang /sessions/guest/checkout.
5. `src/main/java/.../service/ParkingSessionService.java` — gộp logic, sửa validation, set PENDING_PAYMENT.
6. `src/main/java/.../service/PaymentService.java` — set ACTIVE sau confirmPaymentSuccess.
7. `src/main/java/.../service/BuildingService.java` — thêm listFloorsOfBuilding.
8. `src/main/java/.../repository/ParkingSessionRepository.java` — cập nhật query.
9. `src/main/java/.../dto/UnifiedCheckinRequest.java` — file mới.
10. `src/main/java/.../dto/UnifiedCheckinResponse.java` — file mới.
11. `src/main/java/.../dto/FloorDto.java` — file mới.

---

## Phần 5 — Câu hỏi cần anh/chị xác nhận trước khi sửa

1. Có đồng ý **xóa hoàn toàn** `QuickSessionController` sau khi FE chuyển sang `UnifiedCheckin`, hay muốn giữ lại 1-2 phiên bản cũ cho backward-compat?
2. Khi `checkin` mới nhận `plateImage` (multipart), endpoint `/api/sessions/checkin` hiện tại đang nhận multipart `ticketCode` + `plateNumber`. Có muốn tôi **giữ nguyên tất cả field cũ** trong DTO mới (để không vỡ FE hiện tại đang test), hay **breaking change** luôn?
3. Có cần FE cũng sửa luôn trong đợt này không, hay chỉ sửa BE rồi FE tự chuyển sau?
