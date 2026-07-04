package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.*;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;

    @Autowired
    private final EmailService emailService;

    public UserProfileResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setRole("ROLE_DRIVER");
        user.setStatus("ACTIVE");

        return UserProfileResponse.from(userRepository.save(user));
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email not found"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Account is " + user.getStatus().toLowerCase());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Wrong password");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail(), user.getRole(), user.getUserId());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    /**
     * Gửi OTP cho chức năng quên mật khẩu
     */
    public ForgotPasswordResponse sendForgotPasswordOtp(String email) {
        // Kiểm tra email có tồn tại trong hệ thống không
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            ForgotPasswordResponse response = new ForgotPasswordResponse();
            response.setSuccess(false);
            response.setMessage("Email không tồn tại trong hệ thống");
            response.setEmail(email);
            return response;
        }

        // Tạo OTP
        String otp = otpService.generateOtp(email);

        // Gửi email chứa OTP
        String subject = "Mã OTP Đặt Lại Mật Khẩu";
        String htmlContent = """
                <html>
                <body style='font-family:Arial,sans-serif; max-width:600px; margin:0 auto; padding:20px;'>
                    <div style='background-color:#f8f9fa; padding:30px; border-radius:10px;'>
                        <h2 style='color:#007bff; text-align:center; margin-bottom:30px;'>
                            Parking management
                        </h2>
                        <h3 style='color:#28a745; text-align:center; margin-bottom:20px;'>
                            Mã OTP Đặt Lại Mật Khẩu
                        </h3>
                        <div style='background-color:#ffffff; padding:20px; border-radius:8px; border:2px solid #007bff; text-align:center; margin:20px 0;'>
                            <h1 style='color:#007bff; font-size:32px; letter-spacing:5px; margin:0;'>
                                %s
                            </h1>
                        </div>
                        <p style='color:#6c757d; text-align:center; margin-bottom:15px;'>
                            <strong>Mã OTP này có hiệu lực trong 5 phút</strong>
                        </p>
                        <p style='color:#6c757d; text-align:center; margin-bottom:15px;'>
                            Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.
                        </p>
                        <hr style='border:none; border-top:1px solid #dee2e6; margin:20px 0;'>
                        <p style='color:#6c757d; font-size:12px; text-align:center;'>
                            Email này được gửi tự động, vui lòng không trả lời.
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(otp);

        try {
            emailService.sendHtmlEmail(email, subject, htmlContent);

            ForgotPasswordResponse response = new ForgotPasswordResponse();
            response.setSuccess(true);
            response.setMessage("Mã OTP đã được gửi đến email của bạn");
            response.setEmail(email);
            return response;
        } catch (Exception e) {
            ForgotPasswordResponse response = new ForgotPasswordResponse();
            response.setSuccess(false);
            response.setMessage("Không thể gửi email. Vui lòng thử lại sau");
            response.setEmail(email);
            return response;
        }
    }

    /**
     * Xác thực OTP
     */
    public boolean verifyOtp(String email, String otp) {
        return otpService.verifyOtp(email, otp);
    }

    /**
     * Đặt lại mật khẩu
     */
    public boolean resetPassword(String email, String otp, String newPassword) {
        // Xác thực OTP trước
        if (!otpService.verifyOtp(email, otp)) {
            return false;
        }

        // Tìm account
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            return false;
        }

        // Cập nhật mật khẩu mới
        User acc = user.get();
        String encryptedPassword = passwordEncoder.encode(newPassword);
        acc.setPasswordHash(encryptedPassword);
        userRepository.save(acc);

        // Xóa OTP sau khi đặt lại mật khẩu thành công
        otpService.removeOtp(email);

        return true;
    }
}
