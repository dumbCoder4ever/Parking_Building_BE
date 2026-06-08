package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserUpdateRequest(
        @NotBlank(message = "Role must not be blank")
        @Pattern(
                regexp = "ROLE_DRIVER|ROLE_STAFF|ROLE_MANAGER|ROLE_ADMIN",
                message = "Role must be one of ROLE_DRIVER, ROLE_STAFF, ROLE_MANAGER, ROLE_ADMIN"
        )
        String role
) {
}
