package fpt.swp391.parkingmanagement.dto;

import lombok.Data;

@Data
public class ForgotPasswordResponse {
    private String message;
    private boolean success;
    private String email;
} 