package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

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

    @NotNull
    private LocalDateTime reservationStart;

    @NotNull
    private LocalDateTime reservationEnd;
}
