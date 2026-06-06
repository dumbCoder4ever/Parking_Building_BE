package fpt.swp391.parkingmanagement.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.UserRequest;
import fpt.swp391.parkingmanagement.dto.UserResponse;
import fpt.swp391.parkingmanagement.dto.UserUpdateRequest;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.exception.DuplicateResourceException;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse createUser(UserRequest request) {
        // Check for duplicates
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists: " + request.getUsername());
        }
        if (request.getGmail() != null && userRepository.existsByEmail(request.getGmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getGmail());
        }
        if (request.getPhone() != null && userRepository.existsByPhoneNumber(request.getPhone())) {
            throw new DuplicateResourceException("Phone number already exists: " + request.getPhone());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setFullName(request.getFullName());
        user.setEmail(request.getGmail());
        user.setPhoneNumber(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");

        return convertToResponse(userRepository.save(user));
    }

    @Override
    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            Optional<User> existingByUsername = userRepository.findByUsername(request.getUsername());
            if (existingByUsername.isPresent() && !existingByUsername.get().getUserId().equals(userId)) {
                throw new DuplicateResourceException("Username already exists: " + request.getUsername());
            }
            user.setUsername(request.getUsername());
        }

        if (request.getGmail() != null && !request.getGmail().equals(user.getEmail())) {
            Optional<User> existingByEmail = userRepository.findByEmail(request.getGmail());
            if (existingByEmail.isPresent() && !existingByEmail.get().getUserId().equals(userId)) {
                throw new DuplicateResourceException("Email already exists: " + request.getGmail());
            }
            user.setEmail(request.getGmail());
        }

        if (request.getPhone() != null && !request.getPhone().equals(user.getPhoneNumber())) {
            Optional<User> existingByPhone = userRepository.findByPhoneNumber(request.getPhone());
            if (existingByPhone.isPresent() && !existingByPhone.get().getUserId().equals(userId)) {
                throw new DuplicateResourceException("Phone number already exists: " + request.getPhone());
            }
            user.setPhoneNumber(request.getPhone());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        return convertToResponse(userRepository.save(user));
    }

    @Override
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        userRepository.delete(user);
    }

    @Override
    public UserResponse getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return convertToResponse(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void changeUserStatus(String userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setStatus(status);
        userRepository.save(user);
    }

    private UserResponse convertToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setGmail(user.getEmail());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}
