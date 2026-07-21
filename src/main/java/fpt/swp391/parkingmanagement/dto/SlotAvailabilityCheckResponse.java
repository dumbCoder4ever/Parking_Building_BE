package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotAvailabilityCheckResponse {
    private String slotId;
    private String slotName;
    private boolean isAvailable;
    private boolean hasActiveReservation;
    private boolean isInSameBuilding;
    private String message;
}