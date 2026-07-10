package fpt.swp391.parkingmanagement.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

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

    private String uploadToFolder(MultipartFile file, String folder, String label) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException(label + " file is empty");
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
