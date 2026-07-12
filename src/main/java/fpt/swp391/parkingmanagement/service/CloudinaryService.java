package fpt.swp391.parkingmanagement.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    public String upload(MultipartFile file) {
        return uploadToFolder(file, "parking-management/avatars", "Avatar");
    }

    public String uploadParkingImage(MultipartFile file) {
        return uploadToFolder(file, "parking-management/sessions", "Parking image");
    }

    /**
     * Upload an image but swallow the exception if Cloudinary is not configured.
     * Returns null when credentials are missing — callers must handle null safely.
     */
    public String uploadParkingImageSafe(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            return uploadParkingImage(file);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isConfigured() {
        return cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    private String uploadToFolder(MultipartFile file, String folder, String label) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException(label + " file is empty");
        }
        if (!isConfigured()) {
            log.warn("Cloudinary is not configured; skipping {} upload", label.toLowerCase());
            return null;
        }
        if (file.getContentType() != null && !file.getContentType().startsWith("image/")) {
            throw new RuntimeException(label + " file must be an image");
        }

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "image",
                            "folder", folder)
            );

            Object secureUrl = uploadResult.get("secure_url");
            if (secureUrl == null) {
                throw new RuntimeException("Cloudinary response missing secure_url");
            }
            return secureUrl.toString();

        } catch (Exception e) {
            String reason = e.getMessage();
            if (reason == null && e.getCause() != null) {
                reason = e.getCause().getMessage();
            }
            throw new RuntimeException("Upload failed: " + (reason == null ? "Unknown error" : reason));
        }
    }
}
