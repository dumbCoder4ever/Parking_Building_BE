package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
@Data
public class UpdateProfileRequest {

    private String fullName;

    @Pattern(regexp = "^(\\+?\\d{9,15})?$", message = "Invalid phone number")
    private String phoneNumber;

    @Email(message = "Invalid email format")
    private String gmail;

    private MultipartFile avatarUrl;
}
