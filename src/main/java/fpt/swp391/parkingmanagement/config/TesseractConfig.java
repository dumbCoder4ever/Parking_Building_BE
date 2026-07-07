package fpt.swp391.parkingmanagement.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class TesseractConfig {

    private Tesseract tesseractInstance;

    @Value("${ocr.tesseract.datapath}")
    private String datapathRaw;

    @Value("${ocr.tesseract.language:eng}")
    private String language;

    @Value("${ocr.tesseract.whitelist:ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-}")
    private String whitelist;

    @Value("${ocr.tesseract.timeout-ms:8000}")
    private int timeoutMs;

    @Bean
    public Tesseract tesseract() {
        String datapath = resolveDatapath(datapathRaw);
        File dir = new File(datapath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Tesseract datapath does not exist: {} - OCR will fail until folder is created", datapath);
        } else {
            File trainedData = new File(dir, language + ".traineddata");
            if (!trainedData.exists()) {
                log.warn("Tesseract traineddata not found: {} - download from https://github.com/tesseract-ocr/tessdata",
                        trainedData.getAbsolutePath());
            } else {
                log.info("Tesseract ready: lang={}, datapath={}", language, dir.getAbsolutePath());
            }
        }

        Tesseract t = new Tesseract();
        t.setDatapath(datapath);
        t.setLanguage(language);
        t.setTessVariable("tessedit_char_whitelist", whitelist);
        t.setTessVariable("preserve_interword_spaces", "1");
        t.setOcrEngineMode(1);
        t.setPageSegMode(7);
        this.tesseractInstance = t;
        return t;
    }

    private String resolveDatapath(String raw) {
        if (raw == null || raw.isBlank()) {
            return Paths.get("tessdata").toAbsolutePath().toString();
        }
        try {
            String normalized = raw.replace('\\', '/').trim();
            Path path = Paths.get(normalized);
            if (!path.isAbsolute()) {
                path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
            }
            return path.toString();
        } catch (Exception e) {
            Path fallback = Paths.get("tessdata").toAbsolutePath().normalize();
            log.warn("Invalid Tesseract datapath '{}', falling back to {}", raw, fallback);
            return fallback.toString();
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Tesseract bean destroyed");
    }
}