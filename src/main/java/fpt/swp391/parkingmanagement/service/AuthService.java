package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.LoginRequest;
import fpt.swp391.parkingmanagement.dto.LoginResponse;
import fpt.swp391.parkingmanagement.dto.RegisterRequest;
import fpt.swp391.parkingmanagement.dto.UserProfileResponse;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserProfileResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setRole("ROLE_DRIVER");
        user.setStatus("ACTIVE");

        return UserProfileResponse.from(userRepository.save(user));
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Username not found"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Account is " + user.getStatus().toLowerCase());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Wrong password");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername(), user.getRole(), user.getUserId());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .build();

        user.setGmail(request.getGmail());

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setRole(User.Role.DRIVER);
 user.setUsername(request.getUserName());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setStatus(User.UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    public LoginResponse  login(LoginRequest loginRequest) {

        User user = userRepository.findByGmail(loginRequest.getGmail()).orElseThrow(()->new RuntimeException("Gmail not found"));
        boolean matches = passwordEncoder.matches(loginRequest.getPassword(), user.getPassword());
        if (!matches) {
            throw new RuntimeException("Wrong password");
        }
        String token = jwtService.generateToken(user.getGmail(), user.getRole().name());

        return new LoginResponse(
                token,
                user.getRole()
        );
    }
}
