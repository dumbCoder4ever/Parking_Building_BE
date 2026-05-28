package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.LoginRequest;
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

        user.setUsername(request.getUsername());

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        //user.setRole("ROLE_USER");
        user.setRole(User.Role.valueOf("ROLE_USER"));

        return userRepository.save(user);
    }

    public String login(LoginRequest loginRequest) {

        User user = userRepository.findByUsername(loginRequest.getUsername()).orElseThrow(()->new RuntimeException("Username not found"));
        boolean matches = passwordEncoder.matches(loginRequest.getPassword(), user.getPassword());
        if (!matches) {
            throw new RuntimeException("Wrong password");
        }
        return jwtService.generateToken(user.getUsername());
    }
}