package fpt.swp391.parkingmanagement.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffSummaryResponse {

    private String userId;
    private String username;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String status;
    private int buildingCount;
    private List<String> buildingIds;
}
