package fpt.swp391.parkingmanagement.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Multipart/form binding sends dates as String. Swagger/FE often send ISO-8601 with
 * timezone suffix (e.g. {@code 2026-07-13T14:32:55.071Z}), which default
 * {@link LocalDateTime} conversion rejects.
 */
@Component
public class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {

    @Override
    public LocalDateTime convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String value = source.trim();
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Invalid date-time format: '" + value
                            + "'. Use ISO-8601, e.g. 2026-07-13T14:32:55 or 2026-07-13T14:32:55.071Z",
                    ex);
        }
    }
}
