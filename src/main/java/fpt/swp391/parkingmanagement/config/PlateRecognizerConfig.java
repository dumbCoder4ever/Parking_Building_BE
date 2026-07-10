package fpt.swp391.parkingmanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class PlateRecognizerConfig {

    @Value("${platerecognizer.api.url:https://api.platerecognizer.com/v1/plate-reader/}")
    private String apiUrl;

    @Bean
    public WebClient plateRecognizerWebClient() {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .build();
    }
}
