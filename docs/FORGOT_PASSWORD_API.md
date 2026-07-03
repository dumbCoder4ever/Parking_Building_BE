# API Forgot Password với OTP

## Tổng quan
Hệ thống cung cấp chức năng quên mật khẩu với xác thực OTP qua email. Quy trình bao gồm 3 bước:
1. Gửi OTP đến email
2. Xác thực OTP
3. Đặt lại mật khẩu

## Các API Endpoints

### 1. Gửi OTP cho quên mật khẩu
**POST** `/api/auth/forgot-password`

**Request Body:**
```json
{
    "email": "user@example.com"
}
```

**Response Success (200):**
```json
{
    "code": 200,
    "message": "Mã OTP đã được gửi đến email của bạn",
    "data": {
        "message": "Mã OTP đã được gửi đến email của bạn",
        "success": true,
        "email": "user@example.com"
    }
}
```

**Response Error (400):**
```json
{
    "code": 400,
    "message": "Email không tồn tại trong hệ thống",
    "data": {
        "message": "Email không tồn tại trong hệ thống",
        "success": false,
        "email": "user@example.com"
    }
}
```

### 2. Xác thực OTP
**POST** `/api/auth/verify-otp`

**Request Body:**
```json
{
    "email": "user@example.com",
    "otp": "123456"
}
```

**Response Success (200):**
```json
{
    "code": 200,
    "message": "OTP hợp lệ",
    "data": {
        "valid": true,
        "email": "user@example.com"
    }
}
```

**Response Error (400):**
```json
{
    "code": 400,
    "message": "OTP không hợp lệ hoặc đã hết hạn",
    "data": {
        "valid": false,
        "email": "user@example.com"
    }
}
```

### 3. Đặt lại mật khẩu
**POST** `/api/auth/reset-password`

**Request Body:**
```json
{
    "email": "user@example.com",
    "otp": "123456",
    "newPassword": "newPassword123"
}
```

**Response Success (200):**
```json
{
    "code": 200,
    "message": "Đặt lại mật khẩu thành công",
    "data": {
        "success": true,
        "email": "user@example.com"
    }
}
```

**Response Error (400):**
```json
{
    "code": 400,
    "message": "OTP không hợp lệ hoặc email không tồn tại",
    "data": {
        "success": false,
        "email": "user@example.com"
    }
}
```

## API Test (Chỉ dành cho development)

### Test gửi OTP
**POST** `/api/test-forgot-password/send-otp?email=user@example.com`

### Test xác thực OTP
**POST** `/api/test-forgot-password/verify-otp?email=user@example.com&otp=123456`

### Test reset password
**POST** `/api/test-forgot-password/reset-password?email=user@example.com&otp=123456&newPassword=newPassword123`

### Test kiểm tra OTP hết hạn
**GET** `/api/test-forgot-password/check-otp-expired?email=user@example.com`

## Tính năng bảo mật

1. **OTP 6 chữ số**: Mã OTP được tạo ngẫu nhiên 6 chữ số
2. **Thời gian hết hạn**: OTP có hiệu lực trong 5 phút
3. **Mã hóa mật khẩu**: Mật khẩu mới được mã hóa bằng BCrypt
4. **Xóa OTP**: OTP được xóa sau khi đặt lại mật khẩu thành công
5. **Validation**: Kiểm tra email tồn tại và format hợp lệ

## Email Template

Email OTP được gửi với template HTML đẹp mắt, bao gồm:
- Logo và branding của hệ thống
- Mã OTP được hiển thị rõ ràng
- Thông tin về thời gian hết hạn
- Hướng dẫn bảo mật

## Lưu ý

1. **Cấu hình Email**: Đảm bảo đã cấu hình SMTP trong `application.yml`
2. **Rate Limiting**: Nên thêm rate limiting để tránh spam
3. **Logging**: Hệ thống ghi log các hoạt động liên quan đến OTP
4. **Monitoring**: Theo dõi tỷ lệ thành công/thất bại của việc gửi email

## Ví dụ sử dụng với cURL

```bash
# 1. Gửi OTP
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com"}'

# 2. Xác thực OTP (sau khi nhận được OTP từ email)
curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "otp": "123456"}'

# 3. Đặt lại mật khẩu
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "otp": "123456", "newPassword": "newPassword123"}'
``` 