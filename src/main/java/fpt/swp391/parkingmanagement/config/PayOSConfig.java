package fpt.swp391.parkingmanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;

@Configuration
public class PayOSConfig {

    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    @Value("${payos.log-level:NONE}")
    private String logLevel;

    @Value("${payos.return-url}")
    private String returnUrl;

    @Value("${payos.cancel-url}")
    private String cancelUrl;

    @Bean
    public PayOS payOS() {
        ClientOptions options = ClientOptions.builder()
                .clientId(clientId)
                .apiKey(apiKey)
                .checksumKey(checksumKey)
                .logLevel(ClientOptions.LogLevel.valueOf(logLevel.toUpperCase()))
                .build();
        return new PayOS(options);
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }
}
