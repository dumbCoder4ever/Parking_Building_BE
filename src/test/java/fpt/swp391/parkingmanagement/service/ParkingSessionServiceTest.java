package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.entity.*;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingSessionServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ParkingSessionRepository parkingSessionRepository;
    @Mock private ParkingSlotRepository parkingSlotRepository;
    @Mock private UserRepository userRepository;
    @Mock private PricingPolicyRepository pricingPolicyRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BuildingStaffRepository buildingStaffRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private PricingService pricingService;
    @Mock private VehicleTypeRepository vehicleTypeRepository;
    @Mock private PlateRecognizerService ocrService;
    @Mock private BuildingRuleService buildingRuleService;
    @Mock private AuditLogService auditLogService;
    @Mock private ZoneStatusSyncService zoneStatusSyncService;

    @InjectMocks private ParkingSessionService parkingSessionService;

    private Ticket testTicket;
    private ParkingSession testSession;
    private ParkingSlot testSlot;
    private Zone testZone;
    private Floor testFloor;
    private Building testBuilding;
    private User testStaff;
    private User testDriver;
    private Reservation testReservation;
    private Vehicle testVehicle;
    private VehicleType testVehicleType;
    private PricingPolicy testPolicy;

    @BeforeEach
    void setUp() {
        // Building -> Floor -> Zone -> Slot
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

        // Vehicle & VehicleType
        testVehicleType = new VehicleType();
        testVehicleType.setVehicleTypeId("VT-CAR");
        testVehicleType.setTypeName("Car");

        testVehicle = new Vehicle();
        testVehicle.setVehicleId("vehicle-1");
        testVehicle.setPlateNumber("30A-12345");
        testVehicle.setVehicleType(testVehicleType);

        // Ticket
        testTicket = new Ticket();
        testTicket.setTicketId("ticket-1");
        testTicket.setTicketCode("TICKET-001");
        testTicket.setIsUsed(true);
        testTicket.setIsLost(false);
        testTicket.setStatus("USED");

        // Reservation
        testReservation = new Reservation();
        testReservation.setReservationId("res-1");
        testReservation.setReservationCode("RES-001");
        testReservation.setReservationStatus("CHECKED_IN");
        testReservation.setReservationStart(LocalDateTime.now().minusHours(1));
        testReservation.setGracePeriodMinutes(0);
        testReservation.setVehicle(testVehicle);
        testReservation.setSlot(testSlot);
        testReservation.setEstimatedFee(new BigDecimal("50000"));

        // Session
        testSession = new ParkingSession();
        testSession.setSessionId("session-1");
        testSession.setTicket(testTicket);
        testSession.setReservation(testReservation);
        testSession.setVehicle(testVehicle);
        testSession.setSlot(testSlot);
        testSession.setSessionStatus("PENDING_PAYMENT");
        testSession.setPaymentStatus("UNPAID");
        testSession.setIncidentAuthorized(false);
        testSession.setCheckinTime(LocalDateTime.now().minusHours(1));
        testSession.setEstimatedFee(new BigDecimal("50000"));

        // Staff
        testStaff = new User();
        testStaff.setUserId("staff-1");
        testStaff.setEmail("staff@test.com");

        // Driver
        testDriver = new User();
        testDriver.setUserId("driver-1");
        testDriver.setEmail("driver@test.com");
        testDriver.setUsername("driver123");

        testReservation.setUser(testDriver);

        // Pricing Policy
        testPolicy = new PricingPolicy();
        testPolicy.setBasePrice(new BigDecimal("10000"));
        testPolicy.setHourlyRate(new BigDecimal("10000"));
    }

    // ======================== CHECKOUT INCIDENT AUTHORIZATION TESTS ========================

    @Test
    @DisplayName("Should allow checkout when incidentAuthorized=true (lost ticket resolved)")
    void testCheckout_WhenIncidentAuthorized_AllowsCheckout() {
        // Given: session has incidentAuthorized=true
        testSession.setIncidentAuthorized(true);
        testTicket.setIsLost(true);

        when(ticketRepository.findByTicketCode("TICKET-001")).thenReturn(Optional.of(testTicket));
        when(parkingSessionRepository.findActiveSessionGraphByTicketId("ticket-1"))
                .thenReturn(Optional.of(testSession));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId("building-1", "staff-1"))
                .thenReturn(true);
        when(pricingService.getActivePolicy("VT-CAR")).thenReturn(testPolicy);
        when(pricingService.calculateByPolicy(any(), anyInt())).thenReturn(new BigDecimal("50000"));
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setPaymentId("pay-1");
            return p;
        });

        CheckoutRequest request = new CheckoutRequest();
        request.setTicketCode("TICKET-001");
        request.setPaymentMethod("CASH");

        // When
        CheckoutResponse response = parkingSessionService.checkout("staff@test.com", request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Should BLOCK checkout when ticket is lost AND incidentAuthorized=false")
    void testCheckout_WhenTicketLostAndNotAuthorized_BlocksCheckout() {
        // Given: ticket is lost but incident not resolved
        testTicket.setIsLost(true);
        testSession.setIncidentAuthorized(false);

        when(ticketRepository.findByTicketCode("TICKET-001")).thenReturn(Optional.of(testTicket));
        when(parkingSessionRepository.findActiveSessionGraphByTicketId("ticket-1"))
                .thenReturn(Optional.of(testSession));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId("building-1", "staff-1"))
                .thenReturn(true);

        CheckoutRequest request = new CheckoutRequest();
        request.setTicketCode("TICKET-001");

        // When/Then
        assertThatThrownBy(() -> parkingSessionService.checkout("staff@test.com", request))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("incident resolution")
                .hasMessageContaining("lost ticket incident");
    }

    @Test
    @DisplayName("Should ALLOW checkout when ticket is lost BUT has valid authorization")
    void testCheckout_WhenTicketLostButAuthorized_AllowsCheckout() {
        // Given: ticket is lost but incident was resolved (authorized)
        testTicket.setIsLost(true);
        testSession.setIncidentAuthorized(true);

        when(ticketRepository.findByTicketCode("TICKET-001")).thenReturn(Optional.of(testTicket));
        when(parkingSessionRepository.findActiveSessionGraphByTicketId("ticket-1"))
                .thenReturn(Optional.of(testSession));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId("building-1", "staff-1"))
                .thenReturn(true);
        when(pricingService.getActivePolicy("VT-CAR")).thenReturn(testPolicy);
        when(pricingService.calculateByPolicy(any(), anyInt())).thenReturn(new BigDecimal("50000"));
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setPaymentId("pay-1");
            return p;
        });

        CheckoutRequest request = new CheckoutRequest();
        request.setTicketCode("TICKET-001");
        request.setPaymentMethod("CASH");

        // When
        CheckoutResponse response = parkingSessionService.checkout("staff@test.com", request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Should ALLOW checkout when ticket is NOT lost (normal checkout)")
    void testCheckout_WhenTicketNotLost_AllowsCheckout() {
        // Given: normal ticket (not lost)
        testTicket.setIsLost(false);
        testSession.setIncidentAuthorized(false);

        when(ticketRepository.findByTicketCode("TICKET-001")).thenReturn(Optional.of(testTicket));
        when(parkingSessionRepository.findActiveSessionGraphByTicketId("ticket-1"))
                .thenReturn(Optional.of(testSession));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId("building-1", "staff-1"))
                .thenReturn(true);
        when(pricingService.getActivePolicy("VT-CAR")).thenReturn(testPolicy);
        when(pricingService.calculateByPolicy(any(), anyInt())).thenReturn(new BigDecimal("50000"));
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setPaymentId("pay-1");
            return p;
        });

        CheckoutRequest request = new CheckoutRequest();
        request.setTicketCode("TICKET-001");
        request.setPaymentMethod("CASH");

        // When
        CheckoutResponse response = parkingSessionService.checkout("staff@test.com", request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentStatus()).isEqualTo("PAID");
    }

    // ======================== DRIVER CHECKOUT BY SESSION (incident flow) ========================

    @Test
    @DisplayName("Should allow driverCheckoutBySession when incidentAuthorized=true")
    void testDriverCheckoutBySession_WhenAuthorized_AllowsCheckout() {
        // Given: session authorized via incident
        testSession.setIncidentAuthorized(true);
        testSession.setTicket(null); // no ticket

        when(parkingSessionRepository.findByIdGraph("session-1")).thenReturn(Optional.of(testSession));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId("building-1", "staff-1"))
                .thenReturn(true);
        when(pricingService.getActivePolicy("VT-CAR")).thenReturn(testPolicy);
        when(pricingService.calculateByPolicy(any(), anyInt())).thenReturn(new BigDecimal("50000"));
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setPaymentId("pay-1");
            return p;
        });

        // When
        CheckoutResponse response = parkingSessionService.driverCheckoutBySession(
                "staff@test.com", "session-1", null);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Should BLOCK driverCheckoutBySession when incidentAuthorized=false and no ticket")
    void testDriverCheckoutBySession_WhenNotAuthorizedAndNoTicket_BlocksCheckout() {
        // Given: session not authorized and no ticket
        testSession.setIncidentAuthorized(false);
        testSession.setTicket(null);

        when(parkingSessionRepository.findByIdGraph("session-1")).thenReturn(Optional.of(testSession));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId("building-1", "staff-1"))
                .thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> parkingSessionService.driverCheckoutBySession(
                "staff@test.com", "session-1", null))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("authorize")
                .hasMessageContaining("incident");
    }

    @Test
    @DisplayName("Should BLOCK driverCheckoutBySession when ticket is lost and not authorized")
    void testDriverCheckoutBySession_WhenTicketLostAndNotAuthorized_BlocksCheckout() {
        // Given: ticket is lost but not authorized
        testTicket.setIsLost(true);
        testSession.setIncidentAuthorized(false);
        testSession.setTicket(testTicket);

        when(parkingSessionRepository.findByIdGraph("session-1")).thenReturn(Optional.of(testSession));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId("building-1", "staff-1"))
                .thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> parkingSessionService.driverCheckoutBySession(
                "staff@test.com", "session-1", null))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("authorize")
                .hasMessageContaining("incident");
    }

    // ======================== CONFIRM EXIT AND CHECKOUT TESTS ========================

    @Test
    @DisplayName("Should allow confirmExitAndCheckout with incident authorized")
    void testConfirmExitAndCheckout_WhenAuthorized_AllowsCheckout() {
        // Given: incident authorized
        testSession.setIncidentAuthorized(true);
        testTicket.setIsLost(true);

        when(parkingSessionRepository.findByIdGraph("session-1")).thenReturn(Optional.of(testSession));
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(testStaff));
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId("building-1", "staff-1"))
                .thenReturn(true);
        when(pricingService.getActivePolicy("VT-CAR")).thenReturn(testPolicy);
        when(pricingService.calculateByPolicy(any(), anyInt())).thenReturn(new BigDecimal("50000"));
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setPaymentId("pay-1");
            return p;
        });

        // When
        CheckoutResponse response = parkingSessionService.confirmExitAndCheckout(
                "staff@test.com", "session-1", "CASH", null);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSessionStatus()).isEqualTo("COMPLETED");
    }

    // NOTE: confirmExitAndCheckout does NOT have incident authorization check.
    // It is expected to allow checkout regardless of incident status.
    // The incident authorization check is only in checkout() and driverCheckoutBySession() methods.
}
