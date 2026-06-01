package fpt.swp391.parkingmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:contexttest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=8f4a1b9c2d7e6f5a4b3c2d1e9f8a7b6c123456789abcdef",
        "jwt.expiration=86400000"
})
class ParkingmanagementApplicationTests {

    @Test
    void contextLoads() {
    }
}
