package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CreateReservationRequest {

    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    @NotBlank(message = "Vehicle color is required")
    private String vehicleColor;

    @NotBlank(message = "Brand is required")
    private String brand;

    private String model;

    @NotBlank(message = "Vehicle type is required")
    private String vehicleTypeId;

    @NotBlank
    private String slotId;

    @NotNull(message = "Reservation start time is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime reservationStart;

    /** Optional vehicle photo uploaded when driver registers the vehicle for reservation. */
    private MultipartFile image;
}
