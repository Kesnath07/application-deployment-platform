package com.controlcenter.web;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Formatting helpers exposed to Thymeleaf templates as {@code @fmt}. */
@Component("fmt")
public class ViewFormatter {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    public String time(Instant instant) {
        return instant == null ? "—" : TIMESTAMP.format(instant);
    }

    public String duration(Instant start, Instant end) {
        if (start == null || end == null) {
            return "—";
        }
        long seconds = Math.max(0, Duration.between(start, end).toSeconds());
        return seconds < 60 ? seconds + "s" : "%dm %02ds".formatted(seconds / 60, seconds % 60);
    }

    /** Shortens 40-character Git SHAs for display while leaving other tags untouched. */
    public String shortTag(String tag) {
        if (tag == null) {
            return "—";
        }
        return tag.matches("[0-9a-f]{40}") ? tag.substring(0, 12) : tag;
    }
}
