package fpt.swp391.parkingmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.beans.BeanProperty;

@Configuration
public class OpenApiConfig {
   @Bean
   public OpenAPI customOpenAPI() {
return new OpenAPI().info(new Info().title("Parking Management").description("Parking Management API").version("1.0").description("Parking Management API Documentation").license(new License().name("Apache 2.0").url("http://springdoc.org")));

   }
}
