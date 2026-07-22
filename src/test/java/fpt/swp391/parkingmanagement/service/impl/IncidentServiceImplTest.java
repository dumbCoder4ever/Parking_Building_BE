package fpt.swp391.parkingmanagement.service.impl;

import fpt.swp391.parkingmanagement.dto.AvailableSlotResponse;
import fpt.swp391.parkingmanagement.dto.IncidentRequest;
import fpt.swp391.parkingmanagement.dto.IncidentResponse;
import fpt.swp391.parkingmanagement.dto.IncidentUpdateRequest;
import fpt.swp391.parkingmanagement.dto.LatestReservationResponse;
import fpt.swp391.parkingmanagement.dto.SlotAvailabilityCheckResponse;
import fpt.swp391.parkingmanagement.dto.VerifyVehicleRequest;
import fpt.swp391.parkingmanagement.dto.VerifyVehicleResponse;
import fpt.swp391.parkingmanagement.entity.*;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceImplTest {
    @Mock private IncidentRepository incidentRepository;
    @Mock private ParkingSessionRepository parkingSessionRepository;
    @Mock private ParkingSlotRepository parkingSlotRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private UserRepository userRepository;
    @Mock private BuildingStaffRepository buildingStaffRepository;
    @InjectMocks private IncidentServiceImpl incidentService;

    private Incident testIncident;
    private ParkingSession testSession;
    private ParkingSlot testSlot;
    private ParkingSlot testNewSlot;
    private ParkingSlot testDifferentFloorSlot;
    private Vehicle testVehicle;
    private Ticket testTicket;
    private Reservation testReservation;
    private Reservation testLatestReservation;
    private Zone testZone;
    private Floor testFloor;
    private Building testBuilding;
    private Zone testZone2;
    private Floor testFloor2;
    private Building testBuilding2;
    private User testDriver;
    private User testStaff;

    @BeforeEach
    void setUp() {
        // Building 1 / Floor 1 / Zone 1 - for session current slot and reservation
        testBuilding = new Building();
        testBuilding.setBuildingId("building-1");
        testBuilding.setBuildingName("Building A");
        testFloor = new Floor();
        testFloor.setFloorId("floor-1");
        testFloor.setFloorName("Floor 1");
        testFloor.setFloorLevel(1);
        testFloor.setBuilding(testBuilding);
        testZone = new Zone();
        testZone.setZoneId("zone-1");
        testZone.setZoneName("Zone A");
        testZone.setFloor(testFloor);
        testSlot = new ParkingSlot();
        testSlot.setSlotId("slot-1");
        testSlot.setSlotName("A-01");
        testSlot.setSlotStatus("OCCUPIED");
        testSlot.setZone(testZone);

        // Same building, same floor - for reassign success
        testNewSlot = new ParkingSlot();
        testNewSlot.setSlotId("slot-2");
        testNewSlot.setSlotName("A-02");
        testNewSlot.setSlotStatus("AVAILABLE");
        testNewSlot.setZone(testZone);

        // Same building but different floor (for "different floor" test)
        testFloor2 = new Floor();
        testFloor2.setFloorId("floor-2");
        testFloor2.setFloorName("Floor 2");
        testFloor2.setFloorLevel(2);
        testFloor2.setBuilding(testBuilding);
        testZone2 = new Zone();
        testZone2.setZoneId("zone-2");
        testZone2.setZoneName("Zone B");
        testZone2.setFloor(testFloor2);
        testDifferentFloorSlot = new ParkingSlot();
        testDifferentFloorSlot.setSlotId("slot-3");
        testDifferentFloorSlot.setSlotName("B-01");
        testDifferentFloorSlot.setSlotStatus("AVAILABLE");
        testDifferentFloorSlot.setZone(testZone2);

        // Building 2 - for cross-building rejection
        testBuilding2 = new Building();
        testBuilding2.setBuildingId("building-2");
        testBuilding2.setBuildingName("Building B");

        // Driver
        testDriver = new User();
        testDriver.setUserId("user-driver-1");
        testDriver.setEmail("driver@test.com");
        testDriver.setFullName("Test Driver");

        VehicleType testVehicleType = new VehicleType();
        testVehicleType.setVehicleTypeId("VT-CAR");
        testVehicleType.setTypeName("Car");

        testVehicle = new Vehicle();
        testVehicle.setVehicleId("vehicle-1");
        testVehicle.setPlateNumber("30A-12345");
        testVehicle.setUser(testDriver);
        testVehicle.setVehicleType(testVehicleType);
        testTicket = new Ticket();
        testTicket.setTicketId("ticket-1");
        testTicket.setTicketCode("TICKET-ABC123");
        testTicket.setIsUsed(false);
        testReservation = new Reservation();
        testReservation.setReservationId("res-1");
        testReservation.setSlot(testSlot);
        testReservation.setReservationStatus("CHECKED_IN");
        testSession = new ParkingSession();
        testSession.setSessionId("session-1");
        testSession.setVehicle(testVehicle);
        testSession.setTicket(testTicket);
        testSession.setSlot(testSlot);
        testSession.setReservation(testReservation);
        testSession.setEstimatedFee(new BigDecimal("50000"));
        testSession.setTotalFee(new BigDecimal("50000"));
        testSession.setIncidentAuthorized(false);

        // Latest active reservation
        testLatestReservation = new Reservation();
        testLatestReservation.setReservationId("res-latest");
        testLatestReservation.setReservationCode("RES-LATEST-001");
        testLatestReservation.setReservationStatus("APPROVED");
        testLatestReservation.setUser(testDriver);
        testLatestReservation.setVehicle(testVehicle);
        testLatestReservation.setSlot(testSlot);

        // Test staff - assigned to building-1
        testStaff = new User();
        testStaff.setUserId("user-staff-1");
        testStaff.setEmail("staff@test.com");
        testStaff.setFullName("Test Staff");
    }

    private Incident createIncident(String type, String status) {
        Incident incident = new Incident();
        incident.setIncidentId("incident-1");
        incident.setSession(testSession);
        incident.setIncidentType(type);
        incident.setStatus(status);
        incident.setReportSource("DRIVER");
        incident.setReporterId("driver@test.com");
        incident.setVerificationResult("PENDING");
        incident.setCreatedAt(LocalDateTime.now());
        return incident;
    }

    @Test
    @DisplayName("Should allow OPEN to IN_PROGRESS transition")
    void testOpenToInProgress_Success() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));
        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("PROVIDE_VEHICLE_LOCATION");
        IncidentResponse response = incidentService.updateIncidentStatus("staff@test.com", "incident-1", "IN_PROGRESS", request);
        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Should allow IN_PROGRESS to RESOLVED transition")
    void testInProgressToResolved_Success() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "IN_PROGRESS");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));
        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("AUTHORIZE_CHECKOUT");
        IncidentResponse response = incidentService.updateIncidentStatus("staff@test.com", "incident-1", "RESOLVED", request);
        assertThat(response.getStatus()).isEqualTo("RESOLVED");
        assertThat(testSession.getIncidentAuthorized()).isTrue();
    }

    @Test
    @DisplayName("Should NOT allow OPEN to RESOLVED direct transition")
    void testOpenToResolved_Fails() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("AUTHORIZE_CHECKOUT");
        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "RESOLVED", request))
            .isInstanceOf(BaseAPIException.class)
            .hasMessageContaining("Must transition through IN_PROGRESS");
    }

    @Test
    @DisplayName("Should return MATCH when plate number matches")
    void testVerifyVehicle_PlateMatches() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));
        VerifyVehicleRequest request = new VerifyVehicleRequest();
        request.setPlateNumber("30A-12345");
        VerifyVehicleResponse response = incidentService.verifyVehicleOwnership("incident-1", request, "staff@test.com");
        assertThat(response.getVerificationResult()).isEqualTo("MATCH");
    }

    @Test
    @DisplayName("Should return MISMATCH when plate number does not match")
    void testVerifyVehicle_PlateMismatch() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));
        VerifyVehicleRequest request = new VerifyVehicleRequest();
        request.setPlateNumber("30B-99999");
        VerifyVehicleResponse response = incidentService.verifyVehicleOwnership("incident-1", request, "staff@test.com");
        assertThat(response.getVerificationResult()).isEqualTo("MISMATCH");
    }

    @Test
    @DisplayName("Should allow reassign to AVAILABLE slot in same building")
    void testReassignSlot_Available_Success() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "IN_PROGRESS");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(parkingSlotRepository.findById("slot-2")).thenReturn(Optional.of(testNewSlot));
        when(reservationRepository.existsActiveReservationBySlotId("slot-2")).thenReturn(false);
        // No latest reservation -> fallback
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.empty());
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenReturn(testSession);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));
        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("REASSIGN_SLOT");
        request.setNewSlotId("slot-2");
        IncidentResponse response = incidentService.updateIncidentStatus("staff@test.com", "incident-1", "RESOLVED", request);
        assertThat(testSession.getSlot()).isEqualTo(testNewSlot);
    }

    @Test
    @DisplayName("Should NOT allow reassign to OCCUPIED slot")
    void testReassignSlot_Occupied_Fails() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "IN_PROGRESS");
        testNewSlot.setSlotStatus("OCCUPIED");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(parkingSlotRepository.findById("slot-2")).thenReturn(Optional.of(testNewSlot));
        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("REASSIGN_SLOT");
        request.setNewSlotId("slot-2");
        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "RESOLVED", request))
            .isInstanceOf(BaseAPIException.class)
            .hasMessageContaining("Slot is not available");
    }

    @Test
    @DisplayName("Should NOT allow negative adjusted amount")
    void testUpdatePayment_NegativeAmount_Fails() {
        testIncident = createIncident("DRIVER_INCORRECT_FEE", "IN_PROGRESS");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("UPDATE_PAYMENT");
        request.setAdjustedAmount(new BigDecimal("-1000"));
        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "RESOLVED", request))
            .isInstanceOf(BaseAPIException.class)
            .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Should allow valid adjusted amount")
    void testUpdatePayment_ValidAmount_Success() {
        testIncident = createIncident("DRIVER_INCORRECT_FEE", "IN_PROGRESS");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenReturn(testSession);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));
        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("UPDATE_PAYMENT");
        request.setAdjustedAmount(new BigDecimal("75000"));
        IncidentResponse response = incidentService.updateIncidentStatus("staff@test.com", "incident-1", "RESOLVED", request);
        assertThat(testSession.getTotalFee()).isEqualByComparingTo(new BigDecimal("75000"));
    }

    @Test
    @DisplayName("Should return available for valid replacement slot")
    void testCheckSlotAvailability_Available() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(parkingSlotRepository.findById("slot-2")).thenReturn(Optional.of(testNewSlot));
        when(reservationRepository.existsActiveReservationBySlotId("slot-2")).thenReturn(false);
        SlotAvailabilityCheckResponse response = incidentService.checkSlotAvailabilityForReassignment("incident-1", "slot-2", "staff@test.com");
        assertThat(response.isAvailable()).isTrue();
    }

    // ======================== NEW: Latest Reservation Evidence ========================

    @Test
    @DisplayName("Should return latest reservation for incident")
    void testGetLatestReservation_ReturnsLatestActiveReservation() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.of(testLatestReservation));

        LatestReservationResponse response = incidentService.getLatestReservationForIncident("incident-1", "staff@test.com");

        assertThat(response).isNotNull();
        assertThat(response.getReservationId()).isEqualTo("res-latest");
        assertThat(response.getReservationCode()).isEqualTo("RES-LATEST-001");
        assertThat(response.getReservationStatus()).isEqualTo("APPROVED");
        assertThat(response.getBuildingId()).isEqualTo("building-1");
        assertThat(response.getBuildingName()).isEqualTo("Building A");
        assertThat(response.getFloorId()).isEqualTo("floor-1");
        assertThat(response.getFloorLevel()).isEqualTo(1);
        assertThat(response.getZoneId()).isEqualTo("zone-1");
        assertThat(response.getSlotId()).isEqualTo("slot-1");
        assertThat(response.getSlotName()).isEqualTo("A-01");
        assertThat(response.getVehiclePlate()).isEqualTo("30A-12345");
        assertThat(response.getVehicleType()).isEqualTo("Car");
        assertThat(response.getDriverUserId()).isEqualTo("user-driver-1");
        assertThat(response.getDriverEmail()).isEqualTo("driver@test.com");
    }

    @Test
    @DisplayName("Should throw when driver has no active reservation")
    void testGetLatestReservation_NoReservation_Throws() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getLatestReservationForIncident("incident-1", "staff@test.com"))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("no active reservation");
    }

    @Test
    @DisplayName("Should throw when incident not found")
    void testGetLatestReservation_IncidentNotFound_Throws() {
        when(incidentRepository.findById("incident-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getLatestReservationForIncident("incident-x", "staff@test.com"))
                .isInstanceOf(Exception.class);
    }

    // ======================== NEW: Available slots for reassign ========================

    @Test
    @DisplayName("Should return slots filtered by reservation floor")
    void testGetAvailableSlotsForReassign_FiltersByFloor() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.of(testLatestReservation));
        when(parkingSlotRepository.findAvailableByFloorIdExcludingSlots(
                eq("floor-1"), any()))
                .thenReturn(List.of(testNewSlot));

        List<AvailableSlotResponse> slots = incidentService.getAvailableSlotsForReassign("incident-1", "staff@test.com");

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).getSlotId()).isEqualTo("slot-2");
        assertThat(slots.get(0).getFloorId()).isEqualTo("floor-1");
        assertThat(slots.get(0).getBuildingName()).isEqualTo("Building A");
    }

    @Test
    @DisplayName("Should exclude current session slot from available list")
    void testGetAvailableSlotsForReassign_ExcludesCurrentSlot() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.of(testLatestReservation));

        org.mockito.ArgumentCaptor<java.util.Collection<String>> excludeCaptor =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        when(parkingSlotRepository.findAvailableByFloorIdExcludingSlots(
                eq("floor-1"), excludeCaptor.capture()))
                .thenReturn(List.of(testNewSlot));

        incidentService.getAvailableSlotsForReassign("incident-1", "staff@test.com");

        assertThat(excludeCaptor.getValue()).contains("slot-1");
    }

    @Test
    @DisplayName("Should throw when no latest reservation for available slots")
    void testGetAvailableSlotsForReassign_NoReservation_Throws() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getAvailableSlotsForReassign("incident-1", "staff@test.com"))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("no active reservation");
    }

    // ======================== NEW: Reassign validation - same floor ========================

    @Test
    @DisplayName("Should NOT allow reassign to slot in different floor when reservation exists")
    void testReassignSlot_DifferentFloorFromReservation_Fails() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "IN_PROGRESS");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(parkingSlotRepository.findById("slot-3")).thenReturn(Optional.of(testDifferentFloorSlot));
        when(reservationRepository.existsActiveReservationBySlotId("slot-3")).thenReturn(false);
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.of(testLatestReservation));

        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("REASSIGN_SLOT");
        request.setNewSlotId("slot-3");

        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "RESOLVED", request))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("same floor");
    }

    @Test
    @DisplayName("Should fall back to same-building rule when driver has no reservation")
    void testReassignSlot_NoReservation_FallsbackToSameBuilding() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "IN_PROGRESS");
        // different floor but same building - should succeed because no reservation rule
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(parkingSlotRepository.findById("slot-3")).thenReturn(Optional.of(testDifferentFloorSlot));
        when(reservationRepository.existsActiveReservationBySlotId("slot-3")).thenReturn(false);
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.empty());
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenReturn(testSession);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("REASSIGN_SLOT");
        request.setNewSlotId("slot-3");

        IncidentResponse response = incidentService.updateIncidentStatus("staff@test.com", "incident-1", "RESOLVED", request);
        assertThat(testSession.getSlot()).isEqualTo(testDifferentFloorSlot);
    }

    @Test
    @DisplayName("Should allow reassign to slot in same floor as reservation")
    void testReassignSlot_SameFloorAsReservation_Success() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "IN_PROGRESS");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(parkingSlotRepository.findById("slot-2")).thenReturn(Optional.of(testNewSlot));
        when(reservationRepository.existsActiveReservationBySlotId("slot-2")).thenReturn(false);
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.of(testLatestReservation));
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenReturn(testSession);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setResolutionAction("REASSIGN_SLOT");
        request.setNewSlotId("slot-2");

        IncidentResponse response = incidentService.updateIncidentStatus("staff@test.com", "incident-1", "RESOLVED", request);
        assertThat(testSession.getSlot()).isEqualTo(testNewSlot);
    }

    // ======================== NEW: Cancel with reason ========================

    @Test
    @DisplayName("Should throw when cancelling incident WITHOUT reason")
    void testCancelIncident_NoReason_Throws() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));

        IncidentUpdateRequest request = new IncidentUpdateRequest();
        // No cancelReason set

        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "CANCELLED", request))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("Cancel reason is required");
    }

    @Test
    @DisplayName("Should throw when cancelling incident with blank reason")
    void testCancelIncident_BlankReason_Throws() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));

        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setCancelReason("   ");

        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "CANCELLED", request))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("Cancel reason is required");
    }

    @Test
    @DisplayName("Should allow cancelling incident WITH reason")
    void testCancelIncident_WithReason_Success() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setCancelReason("Driver confirmed they found their ticket");

        IncidentResponse response = incidentService.updateIncidentStatus("staff@test.com", "incident-1", "CANCELLED", request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(testIncident.getResolution()).isEqualTo("Driver confirmed they found their ticket");
    }

    // ======================== NEW: Status transition validation ========================

    @Test
    @DisplayName("Should NOT allow OPEN -> CLOSED directly")
    void testStatusTransition_OPEN_to_CLOSED_Throws() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));

        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "CLOSED", null))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("Must transition through IN_PROGRESS first");
    }

    @Test
    @DisplayName("Should NOT allow IN_PROGRESS -> CLOSED directly")
    void testStatusTransition_IN_PROGRESS_to_CLOSED_Throws() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "IN_PROGRESS");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));

        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "CLOSED", null))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("Must RESOLVE incident before closing");
    }

    @Test
    @DisplayName("Should NOT allow RESOLVED -> CANCELLED")
    void testStatusTransition_RESOLVED_to_CANCELLED_Throws() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "RESOLVED");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));

        IncidentUpdateRequest request = new IncidentUpdateRequest();
        request.setCancelReason("Some reason");

        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "CANCELLED", request))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("Cannot cancel a resolved incident");
    }

    @Test
    @DisplayName("Should NOT allow CLOSED -> any status")
    void testStatusTransition_CLOSED_to_Any_Throws() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "CLOSED");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));

        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "IN_PROGRESS", null))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("Cannot change status of a closed or cancelled incident");
    }

    @Test
    @DisplayName("Should NOT allow CANCELLED -> any status")
    void testStatusTransition_CANCELLED_to_Any_Throws() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "CANCELLED");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));

        assertThatThrownBy(() -> incidentService.updateIncidentStatus("staff@test.com", "incident-1", "IN_PROGRESS", null))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("Cannot change status of a closed or cancelled incident");
    }

    // ======================== NEW: LatestReservationResponse with fee and ticketCode ========================

    @Test
    @DisplayName("Should include ticketCode and reservationFee in LatestReservationResponse")
    void testGetLatestReservation_IncludesFeeAndTicketCode() {
        testIncident = createIncident("DRIVER_LOST_TICKET", "OPEN");
        Ticket ticket = new Ticket();
        ticket.setTicketCode("TICKET-001");
        ticket.setIsLost(true);
        testSession.setTicket(ticket);

        testLatestReservation.setEstimatedFee(new BigDecimal("50000"));

        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.of(testLatestReservation));

        LatestReservationResponse response = incidentService.getLatestReservationForIncident("incident-1", "staff@test.com");

        assertThat(response).isNotNull();
        assertThat(response.getTicketCode()).isEqualTo("TICKET-001");
        assertThat(response.getEstimatedFee()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    // ======================== NEW: Available slots - filter by active reservation ========================

    @Test
    @DisplayName("Should filter out slots with active reservations from available list")
    void testGetAvailableSlotsForReassign_FiltersSlotsWithActiveReservations() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.of(testLatestReservation));

        // testNewSlot is returned by repository but has active reservation
        when(parkingSlotRepository.findAvailableByFloorIdExcludingSlots(eq("floor-1"), any()))
                .thenReturn(List.of(testNewSlot));
        when(reservationRepository.existsActiveReservationBySlotId("slot-2")).thenReturn(true);  // Has active reservation

        List<AvailableSlotResponse> slots = incidentService.getAvailableSlotsForReassign("incident-1", "staff@test.com");

        // slot-2 should be filtered out because it has an active reservation
        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("Should set available=false for slots that are not truly available")
    void testGetAvailableSlotsForReassign_SetsAvailableFalseForOccupiedSlots() {
        testIncident = createIncident("DRIVER_SLOT_OCCUPIED", "OPEN");
        when(incidentRepository.findById("incident-1")).thenReturn(Optional.of(testIncident));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.findBuildingIdsByUserId("user-staff-1")).thenReturn(List.of("building-1"));
        when(reservationRepository.findFirstLatestActiveReservationByUserId("user-driver-1"))
                .thenReturn(Optional.of(testLatestReservation));

        // Slot returned by repo but has active reservation
        when(parkingSlotRepository.findAvailableByFloorIdExcludingSlots(eq("floor-1"), any()))
                .thenReturn(List.of(testNewSlot));
        when(reservationRepository.existsActiveReservationBySlotId("slot-2")).thenReturn(true);

        List<AvailableSlotResponse> slots = incidentService.getAvailableSlotsForReassign("incident-1", "staff@test.com");

        assertThat(slots).isEmpty();  // Should be filtered out entirely
    }
}