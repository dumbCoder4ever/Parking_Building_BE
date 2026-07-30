package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.CreateBuildingRuleRequest;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.BuildingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildingRuleServiceTest {

    @Mock
    private BuildingRuleRepository buildingRuleRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private BuildingRuleService buildingRuleService;

    private Building building;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setBuildingId("building-1");
        building.setOperatingStartTime(LocalTime.of(6, 0));
        building.setOperatingEndTime(LocalTime.of(23, 0));
        when(buildingRepository.findById("building-1")).thenReturn(Optional.of(building));
    }

    @Test
    void createOperatingHoursRule_rejectsTimeOutsideBuildingHours() {
        CreateBuildingRuleRequest request = new CreateBuildingRuleRequest();
        request.setRuleCode("OPERATING_HOURS");
        request.setTitle("Giờ hoạt động");
        request.setRuleValue("01:00-05:00");

        assertThatThrownBy(() -> buildingRuleService.create("building-1", request))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("within building operating hours");
    }

    @Test
    void createOperatingHoursRule_acceptsTimeWithinBuildingHours() {
        when(buildingRuleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateBuildingRuleRequest request = new CreateBuildingRuleRequest();
        request.setRuleCode("OPERATING_HOURS");
        request.setTitle("Giờ hoạt động");
        request.setRuleValue("08:00-22:00");

        buildingRuleService.create("building-1", request);
    }

    @Test
    void createVehicleCurfewRule_rejectsCurfewOutsideBuildingHours() {
        CreateBuildingRuleRequest request = new CreateBuildingRuleRequest();
        request.setRuleCode("VEHICLE_TYPE_CURFEW");
        request.setTitle("Truck curfew");
        request.setRuleValue("Truck:01:00");

        assertThatThrownBy(() -> buildingRuleService.create("building-1", request))
                .isInstanceOf(BaseAPIException.class)
                .hasMessageContaining("Curfew time must be within building operating hours");
    }
}
