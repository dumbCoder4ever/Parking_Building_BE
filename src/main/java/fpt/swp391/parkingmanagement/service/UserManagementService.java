package fpt.swp391.parkingmanagement.service;

import java.util.List;

import fpt.swp391.parkingmanagement.dto.UpdateUserStatusRequest;
import fpt.swp391.parkingmanagement.dto.UserRequest;
import fpt.swp391.parkingmanagement.dto.UserResponse;
import fpt.swp391.parkingmanagement.dto.UserUpdateRequest;

public interface UserManagementService {

    UserResponse createUser(UserRequest request);

    UserResponse changeUserRole(String userId, UserUpdateRequest request);

    void deleteUser(String userId);

    UserResponse getUserById(String userId);

    List<UserResponse> getAllUsers();

    void changeUserStatus(String userId, UpdateUserStatusRequest request);
}
