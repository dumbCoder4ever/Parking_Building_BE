package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.aspectj.bridge.IMessage;

@Data
public class    RegisterRequest {

    @NotBlank(message = "Gmail must not be blank")
    @Schema(
            example = "abc@gmail.com"
    )
    @Email(message = "Invalid email format")
    @Pattern(
            regexp = ".*@gmail\\.com$",
            message = "Only Gmail addresses are allowed"
    )
    private String gmail;
@Schema(example = "123456")
    @NotBlank(message = "Password must not be blank")
    @Size(
            min = 6,
            message = "Password must be at least 6 characters"
    )
    private String password;
@Schema(example = "Van A")
    @NotBlank(message = "UserName must not be blank")
    @Size(
            min = 10,
            message = "UserName must be at least 10 characters"
    )
    private String userName;
@Schema(example = "Nguyen Van A")
    @NotBlank(message = "Full Name must not be blank")
    @Size(
            min = 6,
            message = "Full Name must be at least 6 character"
    )
    private String fullName;
@Schema(example = "0912345678")
@NotBlank(message = "Phone must be not blank")
@Size(min =10 ,
        max =10 ,
message = "Phone Number must be 10 digits")
private String phone;
}