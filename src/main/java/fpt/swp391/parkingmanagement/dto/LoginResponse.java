package fpt.swp391.parkingmanagement.dto;

import fpt.swp391.parkingmanagement.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private User.Role role;
}