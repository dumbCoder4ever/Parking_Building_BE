package fpt.swp391.parkingmanagement.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.AssignStaffBuildingsRequest;
import fpt.swp391.parkingmanagement.dto.StaffAssignmentResponse;
import fpt.swp391.parkingmanagement.dto.StaffSummaryResponse;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.BuildingStaff;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.exception.DuplicateResourceException;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.BuildingStaffRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ManagerStaffService {

    private static final String STAFF_ROLE = "ROLE_STAFF";

    private final BuildingStaffRepository buildingStaffRepository;
    private final BuildingRepository buildingRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<StaffSummaryResponse> getAllStaff() {
        return userRepository.findByRoleOrderByFullNameAsc(STAFF_ROLE).stream()
                .map(this::toStaffSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffAssignmentResponse> getStaffByBuilding(String buildingId) {
        Building building = findBuilding(buildingId);
        return buildingStaffRepository.findByBuildingBuildingIdOrderByAssignedAtDesc(building.getBuildingId()).stream()
                .map(assignment -> toAssignmentResponse(assignment, building))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffAssignmentResponse> getBuildingsByStaff(String userId) {
        User staff = findStaff(userId);
        return buildingStaffRepository.findByUserUserIdOrderByAssignedAtDesc(staff.getUserId()).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffAssignmentResponse> getBuildingsByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return buildingStaffRepository.findByUserUserIdOrderByAssignedAtDesc(user.getUserId()).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    @Transactional
    public StaffAssignmentResponse assignStaffToBuilding(String buildingId, String userId) {
        Building building = findBuilding(buildingId);
        User staff = findStaff(userId);

        return buildingStaffRepository.findByBuildingBuildingIdAndUserUserId(buildingId, userId)
                .map(existing -> toAssignmentResponse(existing, building))
                .orElseGet(() -> {
                    BuildingStaff assignment = new BuildingStaff();
                    assignment.setBuilding(building);
                    assignment.setUser(staff);
                    return toAssignmentResponse(buildingStaffRepository.save(assignment), building);
                });
    }

    @Transactional
    public List<StaffAssignmentResponse> assignStaffToBuildings(String userId, AssignStaffBuildingsRequest request) {
        User staff = findStaff(userId);
        Set<String> uniqueBuildingIds = new LinkedHashSet<>(request.getBuildingIds());

        List<Building> buildings = new ArrayList<>();
        for (String buildingId : uniqueBuildingIds) {
            buildings.add(findBuilding(buildingId));
        }

        List<BuildingStaff> existing = buildingStaffRepository.findByUserUserIdOrderByAssignedAtDesc(userId);
        if (!existing.isEmpty()) {
            buildingStaffRepository.deleteAll(existing);
            buildingStaffRepository.flush(); // ensure deletes hit DB before re-insert (uq_building_staff)
        }

        List<StaffAssignmentResponse> responses = new ArrayList<>();
        for (Building building : buildings) {
            BuildingStaff assignment = new BuildingStaff();
            assignment.setBuilding(building);
            assignment.setUser(staff);
            responses.add(toAssignmentResponse(buildingStaffRepository.save(assignment), building));
        }
        return responses;
    }

    @Transactional
    public void removeStaffFromBuilding(String buildingId, String userId) {
        findBuilding(buildingId);
        findStaff(userId);

        if (!buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId(buildingId, userId)) {
            throw new ResourceNotFoundException("Staff is not assigned to this building");
        }

        buildingStaffRepository.deleteByBuildingBuildingIdAndUserUserId(buildingId, userId);
    }

    private Building findBuilding(String buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found: " + buildingId));
    }

    private User findStaff(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (!STAFF_ROLE.equals(user.getRole())) {
            throw new RuntimeException("Only users with role ROLE_STAFF can be assigned to buildings");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Only ACTIVE staff users can be assigned to buildings");
        }
        return user;
    }

    private StaffSummaryResponse toStaffSummary(User user) {
        List<BuildingStaff> assignments = buildingStaffRepository.findByUserUserIdOrderByAssignedAtDesc(user.getUserId());
        List<String> buildingIds = assignments.stream()
                .map(assignment -> assignment.getBuilding().getBuildingId())
                .toList();

        return StaffSummaryResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus())
                .buildingCount(buildingIds.size())
                .buildingIds(buildingIds)
                .build();
    }

    private StaffAssignmentResponse toAssignmentResponse(BuildingStaff assignment) {
        return toAssignmentResponse(assignment, assignment.getBuilding());
    }

    private StaffAssignmentResponse toAssignmentResponse(BuildingStaff assignment, Building building) {
        User staff = assignment.getUser();
        return StaffAssignmentResponse.builder()
                .assignmentId(assignment.getAssignmentId())
                .userId(staff.getUserId())
                .username(staff.getUsername())
                .fullName(staff.getFullName())
                .email(staff.getEmail())
                .buildingId(building.getBuildingId())
                .buildingName(building.getBuildingName())
                .assignedAt(assignment.getAssignedAt())
                .build();
    }
}
