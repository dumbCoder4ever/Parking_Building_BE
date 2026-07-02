package fpt.swp391.parkingmanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final Tesseract tesseract;

    // Biển số VN có dạng: 30A-123.45 hoặc 30A1-23456 hoặc 51H-999.99
    // Bắt 2 số + 1-2 chữ + 1-5 số (cho phép có/không dấu "-", ".")
    private static final Pattern VN_PLATE_PATTERN =
            Pattern.compile("\\b\\d{2}[A-Z]{1,2}[\\s.\\-]?\\d{3,5}(?:[\\.\\-]?\\d{2})?\\b");

    public OcrResult recognizeFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("imageUrl is required");
        }
        Path tmpFile = null;
        try {
            tmpFile = downloadToTempFile(imageUrl);
            return recognizeFromFile(tmpFile.toFile());
        } catch (IOException e) {
            log.error("Failed to download image from URL: {}", imageUrl, e);
            throw new RuntimeException("Cannot download image: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tmpFile);
        }
    }

    public OcrResult recognizeFromUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required and must not be empty");
        }
        Path tmpFile = null;
        try {
            String suffix = extractSuffix(file.getOriginalFilename(), ".jpg");
            tmpFile = Files.createTempFile("ocr-upload-", suffix);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return recognizeFromFile(tmpFile.toFile());
        } catch (IOException e) {
            log.error("Failed to read uploaded file", e);
            throw new RuntimeException("Cannot read uploaded file: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tmpFile);
        }
    }

    private OcrResult recognizeFromFile(File image) {
        long start = System.currentTimeMillis();
        try {
            String raw = tesseract.doOCR(image);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("OCR raw text ({}ms): {}", elapsed, raw);

            String normalized = normalize(raw);
            List<String> candidates = extractPlateCandidates(normalized);

            return OcrResult.builder()
                    .rawText(raw)
                    .normalizedText(normalized)
                    .plateNumber(pickBest(candidates))
                    .candidates(candidates)
                    .confidence(estimateConfidence(raw, candidates))
                    .build();
        } catch (TesseractException e) {
            log.error("Tesseract OCR failed", e);
            throw new RuntimeException("OCR failed: " + e.getMessage(), e);
        }
    }

    private Path downloadToTempFile(String imageUrl) throws IOException {
        URL url = URI.create(imageUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "ParkingManagementOCR/1.0");
        conn.setInstanceFollowRedirects(true);
        conn.connect();

        int code = conn.getResponseCode();
        if (code >= 400) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " when fetching image");
        }

        String suffix = extractSuffix(conn.getURL().getPath(), ".jpg");
        Path tmp = Files.createTempFile("ocr-url-", suffix);
        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
        return tmp;
    }

    private String extractSuffix(String pathOrName, String def) {
        if (pathOrName == null) return def;
        int dot = pathOrName.lastIndexOf('.');
        if (dot < 0 || dot == pathOrName.length() - 1) return def;
        String ext = pathOrName.substring(dot);
        if (ext.length() > 5) return def;
        return ext;
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        return raw.toUpperCase()
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> extractPlateCandidates(String text) {
        List<String> result = new ArrayList<>();
        Matcher m = VN_PLATE_PATTERN.matcher(text);
        while (m.find()) {
            String cleaned = m.group().replaceAll("[\\s]", "").replace('.', '-');
            if (cleaned.length() >= 6) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private String pickBest(List<String> candidates) {
        if (candidates.isEmpty()) return null;
        // Ưu tiên candidate dài nhất (thường là biển đầy đủ nhất)
        return candidates.stream()
                .max((a, b) -> Integer.compare(a.length(), b.length()))
                .orElse(null);
    }

    private double estimateConfidence(String raw, List<String> candidates) {
        if (raw == null || raw.isBlank()) return 0.0;
        double base = Math.min(1.0, raw.length() / 30.0);
        double bonus = candidates.isEmpty() ? 0.0 : 0.3;
        return Math.min(1.0, base + bonus);
    }

    private void deleteQuietly(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    public record OcrResult(String rawText, String normalizedText, String plateNumber,
                            List<String> candidates, double confidence) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String rawText;
            private String normalizedText;
            private String plateNumber;
            private List<String> candidates;
            private double confidence;

            public Builder rawText(String v) { this.rawText = v; return this; }
            public Builder normalizedText(String v) { this.normalizedText = v; return this; }
            public Builder plateNumber(String v) { this.plateNumber = v; return this; }
            public Builder candidates(List<String> v) { this.candidates = v; return this; }
            public Builder confidence(double v) { this.confidence = v; return this; }

            public OcrResult build() {
                return new OcrResult(rawText, normalizedText, plateNumber,
                        candidates == null ? List.of() : candidates, confidence);
            }
        }
    }
}