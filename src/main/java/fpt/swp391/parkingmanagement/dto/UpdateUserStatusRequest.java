package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {

    @NotBlank(message = "Status must not be blank")
    @Pattern(regexp = "ACTIVE|INACTIVE|BANNED", message = "Status must be ACTIVE, INACTIVE or BANNED")
    private String status;
}
