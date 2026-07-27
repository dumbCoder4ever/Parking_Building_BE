package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.QuickCheckinRequest;
import fpt.swp391.parkingmanagement.dto.QuickCheckinResponse;
import fpt.swp391.parkingmanagement.entity.*;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho luồng Driver Walk-in Check-in.
 *
 * Walk-in flow: xe đã đăng ký với driver (vehicle.user != null),
 * nhưng KHÔNG có reservation ACTIVE. Staff quét ảnh biển số → auto-pick slot → tạo session.
 *
 * Phủ 3 nhánh:
 * - happy case: vehicle.user ACTIVE, plate khớp vehicleType
 * - driver account LOCKED/DEACTIVATED → DRIVER_ACCOUNT_DEACTIVATED
 * - vehicleTypeId không khớp đăng ký → VEHICLE_TYPE_MISMATCH
 * - đã có session ACTIVE cho biển → PLATE_ALREADY_PARKED
 * - hết slot trống → SLOT_NOT_AVAILABLE
 * - guest mượn xe driver (dùng API quickGuestCheckin trực tiếp) → PLATE_ALREADY_PARKED
 * - quickAutoCheckin rẽ đúng nhánh B
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParkingSessionServiceWalkInTest {

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

    @InjectMocks private ParkingSessionService service;

    private QuickCheckinRequest req;
    private Vehicle vehicle;
    private User driver;
    private VehicleType carType;
    private Building building;
    private Zone zone;
    private Floor floor;
    private ParkingSlot slot;
    private PricingPolicy policy;

    private static final String STAFF_EMAIL = "staff@x.com";
    private static final String BUILDING_ID = "B-1";
    private static final String PLATE = "30A-12345";

    @BeforeEach
    void setUp() {
        // ---- Building hierarchy
        building = new Building();
        building.setBuildingId(BUILDING_ID);
        building.setBuildingName("Tower A");
        floor = new Floor();
        floor.setFloorId("F-1");
        floor.setBuilding(building);
        Zone zone = new Zone();
        zone.setZoneId("Z-1");
        zone.setFloor(floor);
        floor.setBuilding(building);

        // ---- Slot
        slot = new ParkingSlot();
        slot.setSlotId("S-1");
        slot.setSlotName("A-01");
        slot.setSlotStatus("AVAILABLE");
        slot.setZone(zone);

        // ---- Driver
        driver = new User();
        driver.setUserId("u-driver-1");
        driver.setUsername("driver01");
        driver.setFullName("Nguyen Van A");
        driver.setPhoneNumber("0901234567");
        driver.setEmail("driver01@example.com");
        driver.setRole("ROLE_DRIVER");
        driver.setStatus("ACTIVE");

        // ---- VehicleType
        carType = new VehicleType();
        carType.setVehicleTypeId("VT-CAR");
        carType.setTypeName("Car");

        // ---- Vehicle
        vehicle = new Vehicle();
        vehicle.setVehicleId("V-1");
        vehicle.setPlateNumber(PLATE);
        vehicle.setUser(driver);
        vehicle.setVehicleType(carType);
        vehicle.setStatus("ACTIVE");

        // ---- PricingPolicy
        VehicleType policyType = new VehicleType();
        policyType.setVehicleTypeId("VT-CAR");
        policy = new PricingPolicy();
        policy.setVehicleType(policyType);
        policy.setBasePrice(new BigDecimal("10000"));
        policy.setHourlyRate(new BigDecimal("5000"));

        // ---- Request
        req = new QuickCheckinRequest();
        req.setBuildingId(BUILDING_ID);
        req.setVehicleTypeId("VT-CAR");
        MultipartFile plateImage = mock(MultipartFile.class);
        when(plateImage.isEmpty()).thenReturn(false);
        req.setPlateImage(plateImage);
        req.setNote("Walk-in test");

        // ---- Common mocks
        // OCR returns PLATE
        when(ocrService.recognizeFromUpload(any())).thenReturn(
                new PlateRecognizerService.OcrResult(
                        PLATE, PLATE, PLATE, java.util.List.of(PLATE), 1.0, null));

        // Staff → staff user
        User staff = new User();
        staff.setUserId("u-staff-1");
        staff.setEmail(STAFF_EMAIL);
        staff.setRole("ROLE_STAFF");
        when(userRepository.findByEmail(STAFF_EMAIL)).thenReturn(Optional.of(staff));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(staff));

        // Staff is assigned to building
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId(eq(BUILDING_ID), anyString()))
                .thenReturn(true);

        // Driver lookup by email → used inside method to set createdBy
        lenient().when(userRepository.findByEmail(STAFF_EMAIL)).thenReturn(Optional.of(staff));

        // Pricing policy → default returns policy
        lenient().when(pricingService.getActivePolicy("VT-CAR")).thenReturn(policy);
        lenient().when(pricingService.calculateByPolicy(eq(policy), anyInt())).thenReturn(new BigDecimal("10000"));

        // Ticket saving
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getTicketId() == null) t.setTicketId("ticket-" + System.nanoTime());
            return t;
        });
        when(ticketRepository.findByReservationReservationId(anyString())).thenReturn(Optional.empty());

        // Parking session saving
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenAnswer(inv -> {
            ParkingSession s = inv.getArgument(0);
            if (s.getSessionId() == null) s.setSessionId("session-" + System.nanoTime());
            return s;
        });

        // Slot assignment (default returns slot for happy path)
        lenient().when(parkingSlotRepository
                .findFirstAvailableByBuildingAndVehicleType(eq(BUILDING_ID), eq("VT-CAR")))
                .thenReturn(Optional.of(slot));
        lenient().when(parkingSlotRepository
                .lockFirstAvailableByBuildingAndVehicleType(eq(BUILDING_ID), eq("VT-CAR")))
                .thenReturn(Optional.of(slot));
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    // ===================================================================
    // UT-1: Happy case — walk-in driver with ACTIVE account
    // ===================================================================
    @Test
    @DisplayName("UT-1 Walk-in: driver ACTIVE + plate owned → tạo session với user, không có reservation")
    void testWalkIn_HappyCase() {
        when(vehicleRepository.findByPlateNumberGraph(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.findByNormalizedPlateNumber(anyString())).thenReturn(Optional.of(vehicle));
        when(parkingSessionRepository.findAnyActiveSessionByVehicleId(anyString())).thenReturn(Optional.empty());

        QuickCheckinResponse resp = invokeWalkIn();

        assertThat(resp).isNotNull();
        assertThat(resp.getCheckinType()).isEqualTo("DRIVER_WALK_IN");
        assertThat(resp.getPlateNumber()).isEqualTo(PLATE);
        assertThat(resp.getDriverUserId()).isEqualTo("u-driver-1");
        assertThat(resp.getDriverUsername()).isEqualTo("driver01");
        assertThat(resp.getDriverFullName()).isEqualTo("Nguyen Van A");
        assertThat(resp.getDriverPhone()).isEqualTo("0901234567");
        assertThat(resp.getDriverEmail()).isEqualTo("driver01@example.com");
        assertThat(resp.getTicketCode()).startsWith("G-");
        assertThat(resp.getEstimatedFee()).isEqualByComparingTo("10000");

        // Verify slot updated
        verify(zoneStatusSyncService).updateSlotStatus(slot, "OCCUPIED");

        // (Notification verify removed — module disabled)

        // Verify audit
        verify(auditLogService).record(
                eq("CHECKIN_WALK_IN"),
                eq("PARKING_SESSION"),
                anyString(),
                eq(BUILDING_ID),
                isNull(),
                eq("PENDING_PAYMENT"),
                contains("Walk-in driver driver01"),
                isNull());
    }

    // ===================================================================
    // UT-2: Driver account LOCKED → DRIVER_ACCOUNT_DEACTIVATED
    // ===================================================================
    @Test
    @DisplayName("UT-2 Walk-in: driver LOCKED → throw DRIVER_ACCOUNT_DEACTIVATED, không tạo session")
    void testWalkIn_DriverDeactivated() {
        driver.setStatus("LOCKED");
        when(vehicleRepository.findByPlateNumberGraph(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.findByNormalizedPlateNumber(anyString())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> invokeWalkIn())
                .isInstanceOf(BaseAPIException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DRIVER_ACCOUNT_DEACTIVATED);

        verify(parkingSessionRepository, never()).save(any());
        verify(zoneStatusSyncService, never()).updateSlotStatus(any(), anyString());
    }

    // ===================================================================
    // UT-3: VehicleTypeId không khớp với đăng ký → VEHICLE_TYPE_MISMATCH
    // ===================================================================
    @Test
    @DisplayName("UT-3 Walk-in: vehicleTypeId mismatch → throw VEHICLE_TYPE_MISMATCH")
    void testWalkIn_VehicleTypeMismatch() {
        req.setVehicleTypeId("VT-MOTORCYCLE"); // FE gửi sai
        when(vehicleRepository.findByPlateNumberGraph(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.findByNormalizedPlateNumber(anyString())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> invokeWalkIn())
                .isInstanceOf(BaseAPIException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VEHICLE_TYPE_MISMATCH);

        verify(parkingSessionRepository, never()).save(any());
    }

    // ===================================================================
    // UT-4: Đã có session ACTIVE cho biển → PLATE_ALREADY_PARKED
    // ===================================================================
    @Test
    @DisplayName("UT-4 Walk-in: đã có session ACTIVE → throw PLATE_ALREADY_PARKED")
    void testWalkIn_ActiveSessionExists() {
        when(vehicleRepository.findByPlateNumberGraph(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.findByNormalizedPlateNumber(anyString())).thenReturn(Optional.of(vehicle));
        ParkingSession active = new ParkingSession();
        active.setSessionId("session-existing");
        active.setSessionStatus("ACTIVE");
        when(parkingSessionRepository.findAnyActiveSessionByVehicleId(anyString())).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> invokeWalkIn())
                .isInstanceOf(BaseAPIException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PLATE_ALREADY_PARKED);

        verify(parkingSessionRepository, never()).save(any());
    }

    // ===================================================================
    // UT-5: Hết slot trống cho vehicleType → SLOT_NOT_AVAILABLE
    // ===================================================================
    @Test
    @DisplayName("UT-5 Walk-in: hết slot trống → throw SLOT_NOT_AVAILABLE")
    void testWalkIn_NoSlotAvailable() {
        when(vehicleRepository.findByPlateNumberGraph(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.findByNormalizedPlateNumber(anyString())).thenReturn(Optional.of(vehicle));
        when(parkingSessionRepository.findAnyActiveSessionByVehicleId(anyString())).thenReturn(Optional.empty());
        when(parkingSlotRepository.lockFirstAvailableByBuildingAndVehicleType(eq(BUILDING_ID), eq("VT-CAR")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> invokeWalkIn())
                .isInstanceOf(BaseAPIException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SLOT_NOT_AVAILABLE);

        verify(parkingSessionRepository, never()).save(any());
    }

    // ===================================================================
    // UT-6: Guest check-in cố tình dùng biển của driver → PLATE_ALREADY_PARKED
    // ===================================================================
    @Test
    @DisplayName("UT-6 Guest: plate thuoc driver -> throw DRIVER_OWNED_PLATE_CANNOT_GUEST_CHECKIN")
    void testGuest_BlockDriverVehicle() {
        // Vehicle lookup: co driver (owner)
        when(vehicleRepository.findByPlateNumberGraph(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.findByNormalizedPlateNumber(anyString())).thenReturn(Optional.of(vehicle));
        // VehicleType lookup (guest flow check vehicleTypeId)
        when(vehicleTypeRepository.findById("VT-CAR")).thenReturn(Optional.of(carType));
        // Reservation lookup cho driver vehicle: khong co
        when(reservationRepository.findByVehicleVehicleId("V-1")).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.quickGuestCheckin(STAFF_EMAIL, req))
                .isInstanceOf(BaseAPIException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DRIVER_OWNED_PLATE_CANNOT_GUEST_CHECKIN);
    }

    // ===================================================================
    // UT-11: PricingPolicy null -> refuse walk-in (tranh mien phi)
    // ===================================================================
    @Test
    @DisplayName("UT-11 Walk-in: chua cau hinh pricing -> throw VEHICLE_TYPE_NOT_FOUND, khong tao session")
    void testWalkIn_NoPricingPolicy() {
        when(vehicleRepository.findByPlateNumberGraph(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.findByNormalizedPlateNumber(anyString())).thenReturn(Optional.of(vehicle));
        when(parkingSessionRepository.findAnyActiveSessionByVehicleId(anyString())).thenReturn(Optional.empty());
        // Policy lookup returns null
        when(pricingService.getActivePolicy("VT-CAR")).thenReturn(null);

        assertThatThrownBy(() -> invokeWalkIn())
                .isInstanceOf(BaseAPIException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VEHICLE_TYPE_NOT_FOUND);

        verify(parkingSessionRepository, never()).save(any());
    }

    // ===================================================================
    // UT-7: OCR fail → OCR_FAILED (test qua quickAutoCheckin)
    // ===================================================================
    @Test
    @DisplayName("UT-7 Auto: OCR fail → throw OCR_FAILED, không có DB change")
    void testAuto_OcrFail() {
        MultipartFile emptyImage = mock(MultipartFile.class);
        when(emptyImage.isEmpty()).thenReturn(true);
        req.setPlateImage(emptyImage);

        assertThatThrownBy(() -> service.quickAutoCheckin(STAFF_EMAIL, req))
                .isInstanceOf(BaseAPIException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OCR_FAILED);
    }

    // ===================================================================
    // UT-8: quickAutoCheckin rẽ nhánh B (DRIVER_WALK_IN)
    // ===================================================================
    @Test
    @DisplayName("UT-8 Auto: vehicle có user, không có reservation → rẽ nhánh DRIVER_WALK_IN")
    void testAuto_RoutesToWalkIn() {
        when(vehicleRepository.findByPlateNumberGraph(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.findByNormalizedPlateNumber(anyString())).thenReturn(Optional.of(vehicle));
        // No reservation
        when(reservationRepository.findFirstPendingByVehicleId("V-1")).thenReturn(Optional.empty());
        // No active session
        when(parkingSessionRepository.findAnyActiveSessionByVehicleId("V-1")).thenReturn(Optional.empty());

        QuickCheckinResponse resp = service.quickAutoCheckin(STAFF_EMAIL, req);

        assertThat(resp.getCheckinType()).isEqualTo("DRIVER_WALK_IN");
        // (Notification verify removed — module disabled)
    }

    // ===================================================================
    // UT-9: Sau walk-in, session phải có user_id set (= driver)
    // ===================================================================
    @Test
    @DisplayName("UT-9 Walk-in: session được save với user field = driver")
    void testWalkIn_SessionPersistsDriver() {
        when(vehicleRepository.findByPlateNumberGraph(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.findByNormalizedPlateNumber(anyString())).thenReturn(Optional.of(vehicle));
        when(parkingSessionRepository.findAnyActiveSessionByVehicleId(anyString())).thenReturn(Optional.empty());

        invokeWalkIn();

        ArgumentCaptor<ParkingSession> cap = ArgumentCaptor.forClass(ParkingSession.class);
        verify(parkingSessionRepository).save(cap.capture());
        ParkingSession persisted = cap.getValue();
        assertThat(persisted.getUser()).isNotNull();
        assertThat(persisted.getUser().getUserId()).isEqualTo("u-driver-1");
        assertThat(persisted.getReservation()).isNull();
        assertThat(persisted.getSessionStatus()).isEqualTo("PENDING_PAYMENT");
    }

    // ===================================================================
    // UT-10: Staff không assigned vào building → UNAUTHORIZED
    // ===================================================================
    @Test
    @DisplayName("UT-10 Walk-in: staff không ở trong building → throw UNAUTHORIZED")
    void testWalkIn_StaffNotAssigned() {
        when(buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId(eq(BUILDING_ID), anyString()))
                .thenReturn(false);

        assertThatThrownBy(() -> invokeWalkIn())
                .isInstanceOf(BaseAPIException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(parkingSessionRepository, never()).save(any());
    }

    // -----------------------------------------------------------------
    // Helper: gọi method mới thông qua reflection vì hiện tại chỉ
    // public qua package-private + service là @Service.
    //
    // Trong thiết kế thật, FE nên gọi qua controller gọi service.quickAutoCheckin()
    // sẽ route vào nhánh B. Test này giả lập điều kiện để kiểm tra
    // logic của quickDriverWalkInCheckin trực tiếp.
    // -----------------------------------------------------------------
    private QuickCheckinResponse invokeWalkIn() {
        // Use quickAutoCheckin sẽ rẽ vào quickDriverWalkInCheckin
        return service.quickAutoCheckin(STAFF_EMAIL, req);
    }

    private static <T> T isNull() {
        return org.mockito.ArgumentMatchers.isNull();
    }
}
