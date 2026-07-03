package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.*;
import fpt.swp391.parkingmanagement.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserProfileResponse profile = authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("User registered successfully", profile));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    /**
     * API gửi OTP cho chức năng quên mật khẩu
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        BaseResponse baseResponse = new BaseResponse();

        try {
            var result = authService.sendForgotPasswordOtp(request.getEmail());

            if (result.isSuccess()) {
                baseResponse.setCode(200);
                baseResponse.setMessage(result.getMessage());
                baseResponse.setData(result);
            } else {
                baseResponse.setCode(400);
                baseResponse.setMessage(result.getMessage());
                baseResponse.setData(result);
            }
        } catch (Exception e) {
            baseResponse.setCode(500);
            baseResponse.setMessage("Có lỗi xảy ra: " + e.getMessage());
        }

        return ResponseEntity.ok(baseResponse);
    }

    /**
     * API xác thực OTP
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        BaseResponse baseResponse = new BaseResponse();

        try {
            boolean isValid = authService.verifyOtp(request.getEmail(), request.getOtp());

            if (isValid) {
                baseResponse.setCode(200);
                baseResponse.setMessage("OTP hợp lệ");
                baseResponse.setData(Map.of("valid", true, "email", request.getEmail()));
            } else {
                baseResponse.setCode(400);
                baseResponse.setMessage("OTP không hợp lệ hoặc đã hết hạn");
                baseResponse.setData(Map.of("valid", false, "email", request.getEmail()));
            }
        } catch (Exception e) {
            baseResponse.setCode(500);
            baseResponse.setMessage("Có lỗi xảy ra: " + e.getMessage());
        }

        return ResponseEntity.ok(baseResponse);
    }

    /**
     * API đặt lại mật khẩu
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        BaseResponse baseResponse = new BaseResponse();

        try {
            boolean success = authService.resetPassword(
                    request.getEmail(),
                    request.getOtp(),
                    request.getNewPassword()
            );

            if (success) {
                baseResponse.setCode(200);
                baseResponse.setMessage("Đặt lại mật khẩu thành công");
                baseResponse.setData(Map.of("success", true, "email", request.getEmail()));
            } else {
                baseResponse.setCode(400);
                baseResponse.setMessage("OTP không hợp lệ hoặc email không tồn tại");
                baseResponse.setData(Map.of("success", false, "email", request.getEmail()));
            }
        } catch (Exception e) {
            baseResponse.setCode(500);
            baseResponse.setMessage("Có lỗi xảy ra: " + e.getMessage());
        }

        return ResponseEntity.ok(baseResponse);
    }
}
