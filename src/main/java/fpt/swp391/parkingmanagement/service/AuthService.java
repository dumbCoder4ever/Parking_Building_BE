package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.LoginRequest;
import fpt.swp391.parkingmanagement.dto.LoginResponse;
import fpt.swp391.parkingmanagement.dto.RegisterRequest;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;

    public User register(RegisterRequest request) {

        User user = new User();

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