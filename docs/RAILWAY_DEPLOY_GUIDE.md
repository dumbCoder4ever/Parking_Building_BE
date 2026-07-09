# Hướng dẫn Deploy lên Railway

## 1. Chuẩn bị Database MySQL trên Railway

### Bước 1.1: Tạo MySQL Database
1. Vào https://railway.app/
2. Click **New Project** → **Add Database** → **MySQL**
3. Đợi MySQL khởi tạo xong
4. Click vào database → tab **Variables** để lấy thông tin connection:
   ```
   MYSQLCONNECTIONSTRING = mysql://<user>:<password>@<host>:<port>/railway
   ```

### Bước 1.2: Chạy Migration
1. Click vào MySQL Database
2. Chọn tab **Query**
3. Copy toàn bộ nội dung từ `docs/db/RAILWAY_FULL_MIGRATION.sql`
4. Paste vào Query Editor và click **Execute**
5. Verify: Kiểm tra các bảng đã được tạo

---

## 2. Deploy Backend lên Railway

### Bước 2.1: Kết nối GitHub Repository
1. Vào https://railway.app/
2. Click **New Project** → **Deploy from GitHub repo**
3. Chọn repository `Parking_Building_BE`
4. Branch: `develop` hoặc `khanh`

### Bước 2.2: Cấu hình Environment Variables
Trong Railway Dashboard → Project → Backend Service → **Variables**, thêm:

```env
# Database (lấy từ MySQL connection string)
MYSQL_HOST=<từ connection string>
MYSQL_PORT=<port, thường là 3306>
MYSQL_USER=<username>
MYSQL_PASSWORD=<password>
SPRING_DATASOURCE_URL=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/railway
SPRING_DATASOURCE_USERNAME=${MYSQL_USER}
SPRING_DATASOURCE_PASSWORD=${MYSQL_PASSWORD}

# JWT
JWT_SECRET=8f4a1b9c2d7e6f5a4b3c2d1e9f8a7b6c123456789abcdef

# Cloudinary (nếu có)
CLOUDINARY_CLOUD_NAME=<your_cloud_name>
CLOUDINARY_API_KEY=<your_api_key>
CLOUDINARY_API_SECRET=<your_api_secret>

# FPT.AI OCR
FPT_AI_API_KEY=c0WXiFmRoccngoLy80ndy0JzAhcidoDK

# Server
PORT=8080
```

### Bước 2.3: Cấu hình Build & Start
Trong Railway Dashboard → Project → Backend Service → **Settings**:

```yaml
# Build Command
./gradlew bootJar

# Start Command  
java -jar build/libs/parkingmanagement-0.0.1-SNAPSHOT.jar
```

### Bước 2.4: Thêm MySQL connection
Click **Add Plugin** → chọn MySQL database đã tạo ở Bước 1

---

## 3. Cách lấy Environment Variables

### Database URL từ Railway MySQL
```
MYSQLCONNECTIONSTRING = mysql://<user>:<password>@<host>:<port>/railway
```

Parse ra:
- `MYSQL_HOST` = phần giữa `@` và `:`
- `MYSQL_PORT` = port sau `@host:`
- `MYSQL_USER` = username
- `MYSQL_PASSWORD` = password

---

## 4. Test Deployment

### 4.1 Kiểm tra Logs
Railway Dashboard → Project → Backend → **Deployments** → Click vào deployment → **Logs**

### 4.2 Test API
```bash
# Health check
curl https://<your-app>.up.railway.app/actuator/health

# Swagger UI
https://<your-app>.up.railway.app/swagger-ui.html
```

---

## 5. Default Login Credentials

| Role    | Username  | Password |
|---------|-----------|----------|
| Admin   | admin     | 123      |
| Manager | manager1  | 123      |
| Staff   | staff1    | 123      |
| Driver  | driver1   | 123      |

---

## 6. Cập nhật Frontend URL

Sau khi deploy thành công, cập nhật trong Railway:
```env
FRONTEND_URL=https://<your-frontend-domain>.vercel.app
```

Và trong `application.properties`:
```properties
frontend.url=https://<your-frontend-domain>.vercel.app
```

---

## 7. Troubleshooting

### Lỗi "Connection refused"
→ Kiểm tra MySQL connection string đúng chưa
→ Đảm bảo MySQL database đã chạy

### Lỗi "Table doesn't exist"
→ Chạy lại migration SQL

### Lỗi "CORS"
→ Kiểm tra biến `cors.allowed.origins=*` trong config

---

## 8. Custom Domain (Optional)

Railway Dashboard → Project → Backend Service → **Settings** → **Networking** → **Custom Domain**
