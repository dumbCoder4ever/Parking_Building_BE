package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.ChangePasswordRequest;
import fpt.swp391.parkingmanagement.dto.UpdateProfileRequest;
import fpt.swp391.parkingmanagement.dto.UpdateUserStatusRequest;
import fpt.swp391.parkingmanagement.dto.UserProfileResponse;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public UserProfileResponse getMyProfile(String username) {
        return UserProfileResponse.from(findByUsername(username));
    }

    public UserProfileResponse updateMyProfile(String username, UpdateProfileRequest request) {
        User user = findByUsername(username);

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(request.getEmail());
        }

        UserProfileResponse updated = UserProfileResponse.from(userRepository.save(user));
        notificationService.sendToUser(username, "USER_PROFILE_UPDATED", updated);
        notificationService.broadcastToAdmins("USER_UPDATED", updated);
        return updated;
    }

    public void changePassword(String username, ChangePasswordRequest request) {
        User user = findByUsername(username);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        notificationService.sendToUser(username, "PASSWORD_CHANGED", null);
    }

    // ─── Admin operations ────────────────────────────────────────────────────

    public List<UserProfileResponse> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(UserProfileResponse::from)
                .toList();
    }

    public UserProfileResponse getUserById(String userId) {
        return UserProfileResponse.from(findById(userId));
    }

    public UserProfileResponse updateUserStatus(String userId, UpdateUserStatusRequest request) {
        User user = findById(userId);
        user.setStatus(request.getStatus());
        UserProfileResponse updated = UserProfileResponse.from(userRepository.save(user));

        notificationService.sendToUser(user.getUsername(), "ACCOUNT_STATUS_CHANGED", updated);
        notificationService.broadcastToAdmins("USER_STATUS_UPDATED", updated);
        return updated;
    }

    public UserProfileResponse updateUserRole(String userId, String role) {
        if (!role.equals("ROLE_USER") && !role.equals("ROLE_STAFF") && !role.equals("ROLE_ADMIN")) {
            throw new RuntimeException("Invalid role: " + role);
        }
        User user = findById(userId);
        user.setRole(role);
        UserProfileResponse updated = UserProfileResponse.from(userRepository.save(user));

        notificationService.sendToUser(user.getUsername(), "ROLE_CHANGED", updated);
        notificationService.broadcastToAdmins("USER_ROLE_UPDATED", updated);
        return updated;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }
}
