# Hướng Dẫn Deploy Spring Boot Backend và MySQL lên Railway

## 1. Chuẩn bị Source Code

Trước khi deploy cần đảm bảo project có các file sau ở thư mục gốc:

```text
src/
build.gradle
settings.gradle
gradlew
gradlew.bat
.java-version
```

### Tạo file `.java-version`

Do Railway mặc định sử dụng Java 21, trong khi project yêu cầu Java 17 nên cần tạo file:

```text
.java-version
```

Nội dung:

```text
17
```

Commit và push lên GitHub.

---

## 2. Deploy MySQL Database lên Railway

### Bước 1: Tạo Database

* Truy cập Railway
* Chọn New Project
* Chọn Add Service
* Chọn Database
* Chọn MySQL

Railway sẽ tạo database MySQL và cung cấp các thông tin:

```text
MYSQL_DATABASE
MYSQL_HOST
MYSQL_PORT
MYSQL_USER
MYSQL_PASSWORD
MYSQL_PUBLIC_URL
```

Ví dụ:

```text
MYSQL_DATABASE=railway
MYSQL_HOST=mysql.railway.internal
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=xxxxxxxx
```

---

### Bước 2: Kết nối MySQL Workbench

Sử dụng thông tin Public URL của Railway.

Ví dụ:

```text
Host: thomas.proxy.rlwy.net
Port: 26455
Username: root
Password: xxxxxxxx
```

Không sử dụng:

```text
mysql.railway.internal
```

vì địa chỉ này chỉ dùng cho các service bên trong Railway.

---

### Bước 3: Import Database

Trong MySQL Workbench:

```text
Administration
→ Data Import/Restore
→ Import from Self-Contained File
```

Chọn file:

```text
setupRailway.sql
```

hoặc file dump của project.

Sau đó:

```text
Start Import
```

Kiểm tra:

```sql
SHOW TABLES;
```

Nếu xuất hiện các bảng:

```text
users
vehicles
buildings
parking_sessions
reservations
tickets
...
```

thì import thành công.

---

### Bước 4: Chạy các Script Migration

Nếu project có các script mới như:

```sql
audit_logs.sql
add_columns.sql
update_status.sql
```

thì chạy trực tiếp trên Railway Database bằng Workbench hoặc Query Tool.

Ví dụ:

```sql
ALTER TABLE parking_sessions
ADD COLUMN checkin_plate_image VARCHAR(500);
```

Sau khi chạy:

```sql
SHOW COLUMNS FROM parking_sessions;
```

để kiểm tra.

---

## 3. Deploy Backend Spring Boot

### Bước 1: Fork hoặc Clone Source Code

Đảm bảo source code đã được push lên GitHub.

Ví dụ:

```text
https://github.com/username/Parking_Building_BE
```

---

### Bước 2: Tạo Service Backend

Trong Railway:

```text
New Service
→ Deploy from GitHub Repo
```

Chọn repository backend.

Chọn đúng branch:

```text
develop
```

hoặc branch đang chứa source code mới nhất.

---

### Bước 3: Cấu hình Environment Variables

Trong Railway:

```text
Backend Service
→ Variables
```

Thêm:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Ví dụ:

```text
SPRING_DATASOURCE_URL=jdbc:mysql://mysql.railway.internal:3306/railway?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh

SPRING_DATASOURCE_USERNAME=root

SPRING_DATASOURCE_PASSWORD=xxxxxxxx
```

Lưu ý:

* Sử dụng MYSQL_HOST nội bộ của Railway.
* Không sử dụng Public URL cho Spring Boot.

---

### Bước 4: Kiểm tra application.properties

Project cần sử dụng biến môi trường:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
```

Không hard-code thông tin database.

---

### Bước 5: Build Project

Railway tự động chạy:

```bash
./gradlew clean build -x check -x test -Pproduction
```

Nếu build thành công sẽ tạo file:

```text
build/libs/parkingmanagement-0.0.1-SNAPSHOT.jar
```

---

### Bước 6: Cấu hình Start Command

Trong Railway:

```text
Settings
→ Start Command
```

Nhập:

```bash
java -Dserver.port=$PORT -jar build/libs/parkingmanagement-0.0.1-SNAPSHOT.jar
```

Nếu không cấu hình đúng có thể xuất hiện lỗi:

```text
ls: cannot access '*/build/libs/*jar'
```

---

### Bước 7: Redeploy

Sau khi cấu hình:

```text
Deployments
→ Redeploy
```

---

## 4. Kiểm Tra Sau Khi Deploy

### Kiểm tra Log

```text
Deployments
→ View Logs
```

Nếu thành công sẽ xuất hiện:

```text
Tomcat started on port ...
Started ParkingManagementApplication
```

---

### Kiểm tra API

Ví dụ:

```text
https://your-app.up.railway.app/swagger-ui/index.html
```

hoặc

```text
https://your-app.up.railway.app/api/auth/login
```

Nếu trả về dữ liệu hoặc Swagger UI hiển thị thì backend đã deploy thành công.

---

## 5. Các Lỗi Thường Gặp

### Lỗi Java Version

```text
No matching toolchains found for requested specification
```

Nguyên nhân:

```text
Thiếu file .java-version
```

Cách khắc phục:

```text
Tạo file .java-version
Nội dung: 17
```

---

### Lỗi Không Tìm Thấy JAR

```text
ls: cannot access '*/build/libs/*jar'
```

Nguyên nhân:

```text
Railway tìm sai vị trí file jar
```

Khắc phục:

```text
Cấu hình Start Command đúng đường dẫn build/libs/*.jar
```

---

### Lỗi Kết Nối Database

```text
Failed to configure a DataSource
```

Nguyên nhân:

```text
Thiếu biến môi trường
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Khắc phục:

```text
Khai báo đầy đủ Variables trong Railway
```
