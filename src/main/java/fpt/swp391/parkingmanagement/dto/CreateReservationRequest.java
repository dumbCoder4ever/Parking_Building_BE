package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReservationRequest {
    @NotBlank
    private String plateNumber;

    private String vehicleColor;
    private String brand;
    private String model;

    @NotBlank
    private String vehicleTypeId;

    @NotNull
    private LocalDateTime reservationStart;

    @NotNull
    private LocalDateTime reservationEnd;
}
