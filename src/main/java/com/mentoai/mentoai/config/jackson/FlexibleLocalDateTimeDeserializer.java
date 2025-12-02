package com.mentoai.mentoai.config.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Allows parsing {@link LocalDateTime} values that may or may not include timezone offsets.
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // 1) Strict local datetime without offset (default behavior)
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // fall back to other formats
        }

        // 2) ISO string with explicit offset (e.g., 2025-12-02T12:37:00+09:00)
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return offsetDateTime.toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // fall back to other formats
        }

        // 3) ISO string with zone information (e.g., ...[Asia/Seoul])
        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(trimmed, DateTimeFormatter.ISO_ZONED_DATE_TIME);
            return zonedDateTime.toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // fall back to other formats
        }

        // 4) Instant with Z suffix
        try {
            Instant instant = Instant.parse(trimmed);
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fall through to error
        }

        throw JsonMappingException.from(p, "Unable to parse date-time: " + trimmed);
    }
}

