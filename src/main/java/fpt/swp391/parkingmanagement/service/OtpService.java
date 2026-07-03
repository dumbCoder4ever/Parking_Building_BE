package fpt.swp391.parkingmanagement.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {
    
    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    
    // Thời gian hết hạn OTP (5 phút)
    private static final int OTP_EXPIRY_MINUTES = 5;
    
    public String generateOtp(String email) {
        // Tạo OTP 6 chữ số
        String otp = String.format("%06d", random.nextInt(1000000));
        
        // Lưu OTP với thời gian tạo
        OtpData otpData = new OtpData(otp, LocalDateTime.now());
        otpStore.put(email, otpData);
        
        return otp;
    }
    
    public boolean verifyOtp(String email, String otp) {
        OtpData otpData = otpStore.get(email);
        
        if (otpData == null) {
            return false;
        }
        
        // Kiểm tra OTP có đúng không
        if (!otpData.getOtp().equals(otp)) {
            return false;
        }
        
        // Kiểm tra OTP có hết hạn chưa
        LocalDateTime expiryTime = otpData.getCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES);
        if (LocalDateTime.now().isAfter(expiryTime)) {
            otpStore.remove(email);
            return false;
        }
        
        return true;
    }
    
    public void removeOtp(String email) {
        otpStore.remove(email);
    }
    
    public boolean isOtpExpired(String email) {
        OtpData otpData = otpStore.get(email);
        if (otpData == null) {
            return true;
        }
        
        LocalDateTime expiryTime = otpData.getCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES);
        return LocalDateTime.now().isAfter(expiryTime);
    }
    
    // Inner class để lưu trữ OTP và thời gian tạo
    private static class OtpData {
        private final String otp;
        private final LocalDateTime createdAt;
        
        public OtpData(String otp, LocalDateTime createdAt) {
            this.otp = otp;
            this.createdAt = createdAt;
        }
        
        public String getOtp() {
            return otp;
        }
        
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
} 