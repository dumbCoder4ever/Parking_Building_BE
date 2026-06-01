package fpt.swp391.parkingmanagement.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateBuildingRequest {

    @NotBlank(message = "Building name is required")
    private String buildingName;

    @NotBlank(message = "Address is required")
    private String address;

    @NotNull(message = "Total floors is required")
    @Min(value = 1, message = "Total floors must be at least 1")
    @Max(value = 5, message = "Total floors must not exceed 5")
    private Integer totalFloors;

    @NotNull(message = "Operating start time is required")
    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", example = "06:00:00")
    private LocalTime operatingStartTime;

    @NotNull(message = "Operating end time is required")
    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", example = "23:00:00")
    private LocalTime operatingEndTime;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^[0-9+\\-\\s]{8,20}$", message = "Invalid contact number format")
    private String contactNumber;
}
