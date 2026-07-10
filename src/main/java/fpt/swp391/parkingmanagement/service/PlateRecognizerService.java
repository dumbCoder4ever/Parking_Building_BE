package fpt.swp391.parkingmanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class PlateRecognizerService {

    private static final String PLATE_RECOGNIZER_URL = "https://api.platerecognizer.com/v1/plate-reader/";

    @Value("${platerecognizer.api.key}")
    private String apiKey;

    @Value("${platerecognizer.regions:vn}")
    private String regions;

    @Value("${platerecognizer.timeout-seconds:30}")
    private Integer timeoutSeconds;

    public record OcrResult(
            String plateNumber,
            List<String> candidates,
            String rawText,
            String normalizedText,
            double confidence
    ) {}

    public OcrResult recognizeFromUrl(String imageUrl) {
        log.info("PlateRecognizer from URL: {}", imageUrl);

        var builder = new org.springframework.http.client.MultipartBodyBuilder();
        builder.part("url", imageUrl);
        builder.part("regions", regions);

        String response = callApi(builder.build());
        return parseResponse(response);
    }

    public OcrResult recognizeFromUpload(byte[] imageBytes, String filename) {
        log.info("PlateRecognizer from upload: {} ({} bytes)", filename, imageBytes.length);

        ByteArrayResource resource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        var builder = new org.springframework.http.client.MultipartBodyBuilder();
        builder.part("upload", resource);
        builder.part("regions", regions);

        String response = callApi(builder.build());
        return parseResponse(response);
    }

    private String callApi(org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> bodyParts) {
        return WebClient.builder()
                .baseUrl(PLATE_RECOGNIZER_URL)
                .build()
                .post()
                .header(HttpHeaders.AUTHORIZATION, "Token " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyParts))
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("PlateRecognizer API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.just("{\"error\":\"" + e.getMessage() + "\"}");
                })
                .block();
    }

    private OcrResult parseResponse(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);

            if (root.has("error") && root.get("error").isTextual()) {
                log.warn("PlateRecognizer error: {}", root.get("error").asText());
                return new OcrResult(null, List.of(), "", "", 0);
            }

            JsonNode results = root.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                log.warn("PlateRecognizer empty results");
                return new OcrResult(null, List.of(), "", "", 0);
            }

            String bestPlate = results.get(0).path("plate").asText(null);
            List<String> candidates = new ArrayList<>();
            for (JsonNode item : results) {
                String plate = item.path("plate").asText(null);
                if (plate != null && !candidates.contains(plate)) {
                    candidates.add(plate);
                }
            }

            String rawText = bestPlate != null ? bestPlate : "";
            String normalized = normalizePlateNumber(bestPlate);
            double confidence = results.get(0).has("score")
                    ? results.get(0).get("score").asDouble(0)
                    : (bestPlate != null ? 0.9 : 0);

            log.info("PlateRecognizer result - bestPlate: {}, candidates: {}, confidence: {}",
                    bestPlate, candidates, confidence);

            return new OcrResult(bestPlate, candidates, rawText, normalized, confidence);

        } catch (Exception e) {
            log.error("Failed to parse PlateRecognizer response: {}", jsonResponse, e);
            return new OcrResult(null, List.of(), "", "", 0);
        }
    }

    private String normalizePlateNumber(String plate) {
        if (plate == null) return null;
        return plate.replaceAll("[\\s.\\-]", "").toUpperCase();
    }
}
