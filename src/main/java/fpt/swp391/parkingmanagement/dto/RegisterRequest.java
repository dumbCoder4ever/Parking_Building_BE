package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.aspectj.bridge.IMessage;

@Data
public class    RegisterRequest {

    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Full name must not be blank")
    @Size(min = 6, message = "Full Name must be at least 6 characters")
    private String fullName;

    @Pattern(regexp = "^(\\+?\\d{9,15})?$", message = "Invalid phone number")
    private String phoneNumber;

    @Email(message = "Invalid email format")
    private String email;
}
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
