package fpt.swp391.parkingmanagement.controller;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fpt.swp391.parkingmanagement.dto.CreateReservationRequest;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
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

    private String token;
    private VehicleType motorbikeType;

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
        User user = new User();
        user.setUsername("testuser-" + suffix);
        user.setEmail("testuser-" + suffix + "@gmail.com");
        user.setUserId(java.util.UUID.randomUUID().toString());
        user.setRole("ROLE_DRIVER");
        user.setStatus("ACTIVE");
        user.setPasswordHash("test-password-hash");
        userRepository.save(user);

        token = jwtService.generateToken(user.getEmail(), user.getRole(), user.getUserId());

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

        ParkingSlot ps = new ParkingSlot();
        ps.setSlotId(java.util.UUID.randomUUID().toString());
        ps.setZone(z);
        ps.setSlotName("S1");
        ps.setSlotStatus("AVAILABLE");
        parkingSlotRepository.save(ps);
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
