package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReservationRequest {

    private String vehicleId;

    private String plateNumber;

    private String vehicleColor;
    private String brand;
    private String model;

    private String vehicleTypeId;

    @NotNull
    private LocalDateTime reservationStart;

    @NotNull
    private LocalDateTime reservationEnd;
}
