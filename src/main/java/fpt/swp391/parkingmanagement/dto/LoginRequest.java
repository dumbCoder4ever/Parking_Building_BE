package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @Schema(example = "abc@gmail.com")
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Invalid email format")
        private String gmail;

    @Schema(example = "123456")
    @NotBlank(message = "Password must not be blank")
    private String password;
}
