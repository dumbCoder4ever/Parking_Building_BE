package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Username must not be blank")
    @Size(
            min = 3,
            max = 20,
            message = "Username must be between 3 and 20 characters"
    )
    private String username;

    @NotBlank(message = "Password must not be blank")
    @Size(
            min = 6,
            message = "Password must be at least 6 characters"
    )
    private String password;
    @NotBlank(message = "Password must not be blank")
    @Size(
            min = 6,
            message = "Full Name must be at least 6 character"
    )
   private String fullName;

}