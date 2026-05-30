package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.UserRequest;
import fpt.swp391.parkingmanagement.dto.UserResponse;
import fpt.swp391.parkingmanagement.dto.UserUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserManagementService {

    UserResponse createUser(UserRequest request);

    UserResponse updateUser(Long userId, UserUpdateRequest request);

    void deleteUser(Long userId);

    UserResponse getUserById(Long userId);

    Page<UserResponse> getAllUsers(Pageable pageable);

    Page<UserResponse> searchUsers(String keyword, String role, String status, Pageable pageable);

    void changeUserStatus(Long userId, String status);
}
