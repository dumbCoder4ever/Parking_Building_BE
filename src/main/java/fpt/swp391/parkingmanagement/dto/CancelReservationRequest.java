package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Cancel reservation request. Drivers can only cancel their own PENDING reservations. Staff can cancel PENDING or CHECKED_IN reservations on behalf of drivers.")
public class CancelReservationRequest {

    @Schema(description = "Cancellation reason (optional). E.g. 'Wrong plate number', 'No longer need parking'.")
    @Size(max = 500, message = "Cancel reason must not exceed 500 characters")
    private String reason;
}
