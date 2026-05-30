package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.LoginRequest;
import fpt.swp391.parkingmanagement.dto.LoginResponse;
import fpt.swp391.parkingmanagement.dto.RegisterRequest;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public User register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        String token = authService.login(request);
        return new LoginResponse(token);
    }
}
