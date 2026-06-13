package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.EnumValuesResponse;
import fpt.swp391.parkingmanagement.enums.ConfirmationStatus;
import fpt.swp391.parkingmanagement.enums.PaidStatus;
import fpt.swp391.parkingmanagement.enums.PaidStatusFilter;
import fpt.swp391.parkingmanagement.enums.PaymentMethod;
import fpt.swp391.parkingmanagement.enums.PaymentStatus;
import fpt.swp391.parkingmanagement.enums.SessionPaymentStatus;
import fpt.swp391.parkingmanagement.enums.SessionStatus;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/enums")
public class EnumController {

    @Operation(summary = "Get all enum values for frontend")
    @GetMapping
    public ResponseEntity<ApiResponse<EnumValuesResponse>> getEnumValues() {
        EnumValuesResponse enums = EnumValuesResponse.builder()
                .paymentStatus(toNames(PaymentStatus.values()))
                .sessionPaymentStatus(toNames(SessionPaymentStatus.values()))
                .sessionStatus(toNames(SessionStatus.values()))
                .paymentMethod(toNames(PaymentMethod.values()))
                .confirmationStatus(toNames(ConfirmationStatus.values()))
                .paidStatus(toNames(PaidStatus.values()))
                .paidStatusFilter(toNames(PaidStatusFilter.values()))
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Enum values retrieved successfully", enums));
    }

    private static List<String> toNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toList());
    }
}
