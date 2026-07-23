package fpt.swp391.parkingmanagement.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.AssignStaffBuildingsRequest;
import fpt.swp391.parkingmanagement.dto.StaffAssignmentResponse;
import fpt.swp391.parkingmanagement.dto.StaffSummaryResponse;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.BuildingStaff;
import fpt.swp391.parkingmanagement.entity.User;
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
        List<User> staffUsers = userRepository.findByRoleOrderByFullNameAsc(STAFF_ROLE);
        if (staffUsers.isEmpty()) {
            return List.of();
        }

        List<String> userIds = staffUsers.stream().map(User::getUserId).toList();
        Map<String, List<String>> buildingIdsByUser = new HashMap<>();
        for (Object[] row : buildingStaffRepository.findBuildingIdsByUserIds(userIds)) {
            String userId = (String) row[0];
            String buildingId = (String) row[1];
            buildingIdsByUser
                    .computeIfAbsent(userId, id -> new ArrayList<>())
                    .add(buildingId);
        }

        return staffUsers.stream()
                .map(user -> toStaffSummary(user, buildingIdsByUser.getOrDefault(user.getUserId(), List.of())))
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
        List<Building> buildings = findBuildings(uniqueBuildingIds);

        List<BuildingStaff> existing = buildingStaffRepository.findByUserUserIdOrderByAssignedAtDesc(userId);
        if (!existing.isEmpty()) {
            buildingStaffRepository.deleteAll(existing);
            buildingStaffRepository.flush(); // ensure deletes hit DB before re-insert (uq_building_staff)
        }

        List<BuildingStaff> toSave = new ArrayList<>(buildings.size());
        for (Building building : buildings) {
            BuildingStaff assignment = new BuildingStaff();
            assignment.setBuilding(building);
            assignment.setUser(staff);
            toSave.add(assignment);
        }

        return buildingStaffRepository.saveAll(toSave).stream()
                .map(assignment -> toAssignmentResponse(assignment, assignment.getBuilding()))
                .toList();
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

    private List<Building> findBuildings(Set<String> buildingIds) {
        if (buildingIds.isEmpty()) {
            return List.of();
        }

        Map<String, Building> found = buildingRepository.findAllById(buildingIds).stream()
                .collect(Collectors.toMap(Building::getBuildingId, b -> b, (a, b) -> a, LinkedHashMap::new));

        for (String buildingId : buildingIds) {
            if (!found.containsKey(buildingId)) {
                throw new ResourceNotFoundException("Building not found: " + buildingId);
            }
        }

        // Preserve request order
        return buildingIds.stream().map(found::get).toList();
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

    private StaffSummaryResponse toStaffSummary(User user, List<String> buildingIds) {
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
