package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.UserRequest;
import fpt.swp391.parkingmanagement.dto.UserResponse;
import fpt.swp391.parkingmanagement.dto.UserUpdateRequest;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.exception.DuplicateResourceException;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
        if (request.getGmail() != null && userRepository.existsByGmail(request.getGmail())) {
            throw new DuplicateResourceException("Gmail already exists: " + request.getGmail());
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Phone number already exists: " + request.getPhone());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setFullName(request.getFullName());
        user.setGmail(request.getGmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.valueOf(request.getRole()));
        user.setStatus(User.UserStatus.valueOf(request.getStatus()));

        User savedUser = userRepository.save(user);
        return convertToResponse(savedUser);
    }

    @Override
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Check for duplicate username
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsernameAndIdNot(request.getUsername(), id)) {
                throw new DuplicateResourceException("Username already exists: " + request.getUsername());
            }
            user.setUsername(request.getUsername());
        }

        // Check for duplicate email
        if (request.getGmail() != null && !request.getGmail().equals(user.getGmail())) {
            if (userRepository.existsByGmailAndIdNot(request.getGmail(), id)) {
                throw new DuplicateResourceException("Gmail already exists: " + request.getGmail());
            }
            user.setGmail(request.getGmail());
        }

        // Check for duplicate phone
        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhoneAndIdNot(request.getPhone(), id)) {
                throw new DuplicateResourceException("Phone number already exists: " + request.getPhone());
            }
            user.setPhone(request.getPhone());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRole() != null) {
            user.setRole(User.Role.valueOf(request.getRole()));
        }

        if (request.getStatus() != null) {
            user.setStatus(User.UserStatus.valueOf(request.getStatus()));
        }

        User updatedUser = userRepository.save(user);
        return convertToResponse(updatedUser);
    }

    @Override
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        userRepository.delete(user);
    }

    @Override
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return convertToResponse(user);
    }

//    @Override
//    public Page<UserResponse> getAllUsers(Pageable pageable) {
//        return userRepository.findAll(pageable).map(this::convertToResponse);
//    }

    @Override
    public List<UserResponse> getAllUsers() {
        List<User> movies = userRepository.findAll();
        return movies.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

//    @Override
//    public Page<UserResponse> searchUsers(String keyword, String role, String status, Pageable pageable) {
//        User.Role roleEnum = null;
//        if (role != null && !role.isEmpty()) {
//            roleEnum = User.Role.valueOf(role);
//        }
//
//        User.UserStatus statusEnum = null;
//        if (status != null && !status.isEmpty()) {
//            statusEnum = User.UserStatus.valueOf(status);
//        }
//
//        return userRepository.searchUsers(keyword, roleEnum, statusEnum, pageable)
//                .map(this::convertToResponse);
//    }

    @Override
    public void changeUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setStatus(User.UserStatus.valueOf(status));
        userRepository.save(user);
    }

    private UserResponse convertToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setGmail(user.getGmail());
        response.setPhone(user.getPhone());
        response.setRole(String.valueOf(user.getRole()));
        response.setStatus(user.getStatus().toString());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}
