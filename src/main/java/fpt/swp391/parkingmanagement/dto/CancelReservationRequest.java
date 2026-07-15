package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu hủy reservation. Driver chỉ hủy được reservation của chính mình khi status = PENDING. Staff có thể hủy giúp driver PENDING hoặc CHECKED_IN.")
public class CancelReservationRequest {

    @Schema(description = "Lý do hủy reservation (tùy chọn). VD: 'Sai biển số', 'Không có nhu cầu gửi xe nữa'.")
    @Size(max = 500, message = "Cancel reason must not exceed 500 characters")
    private String reason;
}
