package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnumValuesResponse {
    private List<String> paymentStatus;
    private List<String> sessionPaymentStatus;
    private List<String> sessionStatus;
    private List<String> paymentMethod;
    private List<String> confirmationStatus;
    private List<String> paidStatus;
    private List<String> paidStatusFilter;
}
