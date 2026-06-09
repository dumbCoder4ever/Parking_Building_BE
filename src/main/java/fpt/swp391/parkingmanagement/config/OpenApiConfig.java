package fpt.swp391.parkingmanagement.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    private static final List<String> API_PATH_ORDER = List.of(
            "/api/vehicles/types",
            "/api/vehicles/types/{vehicleTypeId}",
            "/api/manager/staff",
            "/api/manager/staff/{userId}/buildings",
            "/api/manager/buildings/{buildingId}/staff",
            "/api/manager/buildings/{buildingId}/staff/{userId}",
            "/api/manager/setup/buildings",
            "/api/manager/setup/buildings/{buildingId}",
            "/api/manager/setup/buildings/{buildingId}/status",
            "/api/manager/setup/buildings/{buildingId}/floors",
            "/api/manager/setup/floors/{floorId}",
            "/api/manager/setup/floors/{floorId}/status",
            "/api/manager/setup/floors/{floorId}/zones",
            "/api/manager/setup/zones/{zoneId}/status",
            "/api/manager/setup/zones/{zoneId}/slots",
            "/api/vehicles/me",
            "/api/vehicles/me/{vehicleId}",
            "/api/manager/drivers",
            "/api/manager/drivers/by-username/{username}/vehicles",
            "/api/manager/drivers/{userId}/vehicles",
            "/api/manager/vehicles",
            "/api/manager/vehicles/{vehicleId}/status",
            "/api/manager/parking-sessions/check-in",
            "/api/manager/parking-sessions/{sessionId}/check-out",
            "/api/slots/availability",
            "/api/reservations");

    @Bean
    public OpenAPI customOpenAPI() {

        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Parking Management")
                        .version("1.0")
                        .description("Parking Management API Documentation"))

                // Add Security Requirement
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(securitySchemeName)
                )

                // Define JWT Bearer Scheme
                .schemaRequirement(
                        securitySchemeName,
                        new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                );
    }

    @Bean
    public OpenApiCustomizer managerSetupPathOrderingCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            Paths original = openApi.getPaths();
            Map<String, PathItem> reordered = new LinkedHashMap<>();

            for (String path : API_PATH_ORDER) {
                PathItem pathItem = original.get(path);
                if (pathItem != null) {
                    reordered.put(path, pathItem);
                }
            }

            original.forEach((path, pathItem) -> {
                if (!reordered.containsKey(path)) {
                    reordered.put(path, pathItem);
                }
            });

            Paths paths = new Paths();
            reordered.forEach(paths::addPathItem);
            openApi.setPaths(paths);
        };
    }
}
