package fpt.swp391.parkingmanagement.controller;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fpt.swp391.parkingmanagement.dto.CreateReservationRequest;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.repository.VehicleRepository;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import fpt.swp391.parkingmanagement.repository.ZoneRepository;
import fpt.swp391.parkingmanagement.service.JwtService;
import fpt.swp391.parkingmanagement.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=8f4a1b9c2d7e6f5a4b3c2d1e9f8a7b6c123456789abcdef",
        "jwt.expiration=86400000"
})
@AutoConfigureMockMvc
public class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    private String token;
    private VehicleType motorbikeType;
    private ParkingSlot testSlot;
    private User testUser;

    @BeforeEach
    void setup() {
        ticketRepository.deleteAll();
        reservationRepository.deleteAll();
        vehicleRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        zoneRepository.deleteAll();
        floorRepository.deleteAll();
        buildingRepository.deleteAll();
        vehicleTypeRepository.deleteAll();
        userRepository.deleteAll();

        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);

        testUser = new User();
        testUser.setUsername("testuser-" + suffix);
        testUser.setEmail("testuser-" + suffix + "@gmail.com");
        testUser.setUserId(java.util.UUID.randomUUID().toString());
        testUser.setRole("ROLE_DRIVER");
        testUser.setStatus("ACTIVE");
        testUser.setPasswordHash("test-password-hash");
        userRepository.save(testUser);

        token = jwtService.generateToken(testUser.getEmail(), testUser.getRole(), testUser.getUserId());

        motorbikeType = new VehicleType();
        motorbikeType.setTypeName("Motorbike");
        motorbikeType.setVehicleTypeId(java.util.UUID.randomUUID().toString());
        vehicleTypeRepository.save(motorbikeType);

        Building b = new Building();
        b.setBuildingName("Main");
        b.setBuildingId(java.util.UUID.randomUUID().toString());
        buildingRepository.save(b);

        Floor f = new Floor();
        f.setFloorId(java.util.UUID.randomUUID().toString());
        f.setBuilding(b);
        f.setFloorLevel(1);
        f.setFloorName("Floor 1");
        f.setVehicleType(motorbikeType);
        f.setStatus("ACTIVE");
        floorRepository.save(f);

        Zone z = new Zone();
        z.setZoneId(java.util.UUID.randomUUID().toString());
        z.setFloor(f);
        z.setZoneName("Z1");
        zoneRepository.save(z);

        testSlot = new ParkingSlot();
        testSlot.setSlotId(java.util.UUID.randomUUID().toString());
        testSlot.setZone(z);
        testSlot.setSlotName("S1");
        testSlot.setSlotStatus("AVAILABLE");
        parkingSlotRepository.save(testSlot);
    }

    @Test
    void availabilityEndpoint_returnsCounts() throws Exception {
        var mvcResult = mockMvc.perform(get("/api/slots/availability")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String content = mvcResult.getResponse().getContentAsString();
        assertThat(content).contains("Floor 1");
        assertThat(content).contains("Motorbike");
    }

    @Test
    void createReservation_createsReservationAndTicket() throws Exception {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setPlateNumber("AB-1234");
        req.setVehicleTypeId(motorbikeType.getVehicleTypeId());
        req.setReservationStart(LocalDateTime.now().plusHours(1));
        req.setReservationEnd(LocalDateTime.now().plusHours(3));

        String json = objectMapper.writeValueAsString(req);

        var mvcResult = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();

        String content = mvcResult.getResponse().getContentAsString();
        assertThat(content).contains("reservationCode");
        assertThat(content).contains("ticketCode");
    }

    @Test
    void autoExpirePendingReservation_cancelsWhenGracePeriodExpired() {
        // Tạo PENDING reservation đã hết grace period
        CreateReservationRequest req = new CreateReservationRequest();
        req.setSlotId(testSlot.getSlotId());
        req.setPlateNumber("AUTO-CANCEL-PENDING");
        req.setVehicleTypeId(motorbikeType.getVehicleTypeId());
        req.setReservationStart(LocalDateTime.now().minusHours(3));
        req.setReservationEnd(LocalDateTime.now().minusHours(1)); // Đã hết hạn

        var response = reservationService.createReservation(testUser.getEmail(), req);
        String reservationCode = response.getReservationCode();

        Reservation reservation = reservationRepository.findByReservationCode(reservationCode).orElseThrow();
        assertThat(reservation.getReservationStatus()).isEqualTo("PENDING");
        assertThat(testSlot.getSlotStatus()).isEqualTo("RESERVED");

        reservation.setGracePeriodMinutes(0);
        reservationRepository.save(reservation);

        int expiredCount = reservationService.autoExpireReservations();

        assertThat(expiredCount).isEqualTo(1);

        Reservation cancelledReservation = reservationRepository.findByReservationCode(reservationCode).orElseThrow();
        assertThat(cancelledReservation.getReservationStatus()).isEqualTo("CANCELLED");
        assertThat(cancelledReservation.getNote()).contains("Auto-cancelled");

        ParkingSlot releasedSlot = parkingSlotRepository.findBySlotId(testSlot.getSlotId()).orElseThrow();
        assertThat(releasedSlot.getSlotStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void autoExpireApprovedReservation_expiresWhenGracePeriodExpired() {
        // Tạo APPROVED reservation đã hết grace period
        CreateReservationRequest req = new CreateReservationRequest();
        req.setSlotId(testSlot.getSlotId());
        req.setPlateNumber("AUTO-EXPIRE-APPROVED");
        req.setVehicleTypeId(motorbikeType.getVehicleTypeId());
        req.setReservationStart(LocalDateTime.now().minusHours(3));
        req.setReservationEnd(LocalDateTime.now().minusHours(1)); // Đã hết hạn

        var response = reservationService.createReservation(testUser.getEmail(), req);
        String reservationCode = response.getReservationCode();

        // Approve reservation trước
        reservationService.updateReservationStatus(reservationCode, "APPROVED", "Staff approved");

        Reservation reservation = reservationRepository.findByReservationCode(reservationCode).orElseThrow();
        assertThat(reservation.getReservationStatus()).isEqualTo("APPROVED");
        assertThat(testSlot.getSlotStatus()).isEqualTo("RESERVED");

        reservation.setGracePeriodMinutes(0);
        reservationRepository.save(reservation);

        int expiredCount = reservationService.autoExpireReservations();

        assertThat(expiredCount).isEqualTo(1);

        Reservation expiredReservation = reservationRepository.findByReservationCode(reservationCode).orElseThrow();
        assertThat(expiredReservation.getReservationStatus()).isEqualTo("EXPIRED");
        assertThat(expiredReservation.getNote()).contains("Auto-expired");

        ParkingSlot releasedSlot = parkingSlotRepository.findBySlotId(testSlot.getSlotId()).orElseThrow();
        assertThat(releasedSlot.getSlotStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void autoExpireReservation_doesNotCancelActiveReservation() {
        // Tạo PENDING reservation còn hiệu lực (end time trong tương lai)
        CreateReservationRequest req = new CreateReservationRequest();
        req.setSlotId(testSlot.getSlotId());
        req.setPlateNumber("ACTIVE-RESERVATION");
        req.setVehicleTypeId(motorbikeType.getVehicleTypeId());
        req.setReservationStart(LocalDateTime.now().plusHours(1));
        req.setReservationEnd(LocalDateTime.now().plusHours(3));

        reservationService.createReservation(testUser.getEmail(), req);

        int expiredCount = reservationService.autoExpireReservations();

        assertThat(expiredCount).isEqualTo(0);

        Reservation reservation = reservationRepository.findAll().get(0);
        assertThat(reservation.getReservationStatus()).isEqualTo("PENDING");
    }

    @Test
    void autoExpireReservation_doesNotCancelAlreadyCancelledReservation() {
        // Tạo PENDING reservation rồi cancel trước
        CreateReservationRequest req = new CreateReservationRequest();
        req.setSlotId(testSlot.getSlotId());
        req.setPlateNumber("CANCELLED-TEST");
        req.setVehicleTypeId(motorbikeType.getVehicleTypeId());
        req.setReservationStart(LocalDateTime.now().minusHours(3));
        req.setReservationEnd(LocalDateTime.now().minusHours(1));

        var response = reservationService.createReservation(testUser.getEmail(), req);

        reservationService.updateReservationStatus(response.getReservationCode(), "CANCELLED", "Manual cancellation");

        int expiredCount = reservationService.autoExpireReservations();

        assertThat(expiredCount).isEqualTo(0);
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper;
        }
    }
}
