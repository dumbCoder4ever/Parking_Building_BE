package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStaffRequestDto {

    @NotBlank(message = "Request ID is required")
    private String requestId;

    private boolean approved;

    private String rejectionReason;
}
