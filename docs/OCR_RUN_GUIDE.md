# Cách chạy app sau khi thêm Tesseract OCR

## Vấn đề

App lỗi khi chạy từ IDE:
```
java.lang.NoClassDefFoundError: net/sourceforge/tess4j/Tesseract
```

**Nguyên nhân:** Classpath trong Run Configuration của IDE được cache cũ — thiếu `tess4j-5.13.0.jar` và các dependency `runtimeOnly`/`runtimeClasspath` mới.

Khi build bằng Gradle thì OK vì Gradle tự resolve classpath đầy đủ.

---

## Cách chạy CHẮC CHẮN ĐÚNG

### Phương án 1: Dùng Gradle (Recommended - chạy OK 100%)

Mở PowerShell tại `C:\Users\Admin\Downloads\BE\Parking_Building_BE`, chạy:

```powershell
.\gradlew.bat bootRun
```

Lệnh này:
- Tự resolve toàn bộ dependency (compile + runtime + runtimeOnly)
- Đảm bảo `tess4j-5.13.0.jar` được include vào classpath
- Hot reload code khi đổi Java file
- Dừng = Ctrl+C

Test nhanh OCR endpoint sau khi boot xong:
```powershell
curl -X POST http://localhost:8080/api/ocr/plate `
  -H "Authorization: Bearer <JWT_TOKEN>" `
  -H "Content-Type: application/json" `
  -d '{"imageUrl":"https://res.cloudinary.com/xxx/sample-plate.jpg"}'
```

---

### Phương án 2: Fix IDE Run Configuration

Trong IntelliJ IDEA:

1. Mở **Run → Edit Configurations...**
2. Chọn config `ParkingmanagementApplication`
3. Ở panel **Build and run**:
   - Đổi **Build runner** từ `IntelliJ IDEA` → **`Gradle`**
   - **Gradle project**: `parkingmanagement`
   - **Task**: `bootRun`
4. Save → Run lại

Cách này IDE sẽ dùng Gradle classpath thay vì tự build.

---

### Phương án 3: Boot jar

```powershell
.\gradlew.bat bootJar
java -jar build\libs\parkingmanagement-0.0.1-SNAPSHOT.jar
```

File jar đã có sẵn tất cả dependency ở `BOOT-INF/lib/` — không thiếu gì.

---

## Sau khi chạy được, kiểm tra OCR

### 1. Tessdata phải có sẵn

Tạo folder và copy file traineddata:
```powershell
mkdir C:\tessdata
# Copy eng.traineddata vào C:\tessdata\eng.traineddata
```

Nếu dùng folder khác:
```powershell
$env:TESSDATA_PATH = "D:\my-tessdata"
.\gradlew.bat bootRun
```

### 2. Test nhanh

Gọi endpoint test (cần JWT của staff):
```powershell
$token = "eyJhbGc..."   # JWT của staff
$body = @{ imageUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg" } | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/ocr/plate" `
  -Method POST `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $body
```

Response mẫu:
```json
{
  "plateNumber": "30A-12345",
  "candidates": ["30A-12345", "30A-123.45"],
  "rawText": "30A-123.45",
  "normalizedText": "30A-123.45",
  "confidence": 0.85
}
```

### 3. Upload multipart

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/ocr/plate/upload" `
  -Method POST `
  -Headers @{ Authorization = "Bearer $token" } `
  -Form @{ file = Get-Item "C:\Users\Admin\Desktop\plate.jpg" }
```

---

## Lỗi thường gặp

### `Tesseract datapath does not exist`
→ Tạo folder `C:\tessdata` (hoặc set `TESSDATA_PATH`)

### `Tesseract traineddata not found`
→ Download `eng.traineddata` tại https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata
→ Copy vào `C:\tessdata\eng.traineddata`

### `Tesseract: Could not initialize Tesseract` (Windows)
→ Cài Tesseract native binary: https://github.com/UB-Mannheim/tesseract/wiki
→ Đảm bảo `C:\Program Files\Tesseract-OCR\tesseract.exe` có trong PATH

### `UnsatisfiedLinkError: no lept4j in java.library.path`
→ tess4j cần `libgcc_s_seh-1.dll`, `libstdc++-6.dll`, `libleptonica-6.dll`, `libtesseract-6.dll`
→ Tải từ https://github.com/nguyenq/tess4j/releases (file `tess4j-5.x.x-x86_64.zip`)

### ClassPath vẫn thiếu khi chạy từ IDE
→ Chuyển sang Phương án 1 (bootRun) hoặc 3 (bootJar)