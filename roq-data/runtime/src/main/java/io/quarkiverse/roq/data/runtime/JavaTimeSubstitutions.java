package io.quarkiverse.roq.data.runtime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import io.quarkus.runtime.ObjectSubstitution;

/**
 * Lets data mapping beans with java.time fields be bytecode recorded: the recorder cannot rebuild these immutable
 * types itself, so each is recorded as its ISO-8601 string and parsed back at startup.
 */
public final class JavaTimeSubstitutions {

    private JavaTimeSubstitutions() {
    }

    public static class LocalDates implements ObjectSubstitution<LocalDate, String> {
        public String serialize(LocalDate obj) {
            return obj.toString();
        }

        public LocalDate deserialize(String obj) {
            return LocalDate.parse(obj);
        }
    }

    public static class LocalDateTimes implements ObjectSubstitution<LocalDateTime, String> {
        public String serialize(LocalDateTime obj) {
            return obj.toString();
        }

        public LocalDateTime deserialize(String obj) {
            return LocalDateTime.parse(obj);
        }
    }

    public static class LocalTimes implements ObjectSubstitution<LocalTime, String> {
        public String serialize(LocalTime obj) {
            return obj.toString();
        }

        public LocalTime deserialize(String obj) {
            return LocalTime.parse(obj);
        }
    }

    public static class Instants implements ObjectSubstitution<Instant, String> {
        public String serialize(Instant obj) {
            return obj.toString();
        }

        public Instant deserialize(String obj) {
            return Instant.parse(obj);
        }
    }

    public static class OffsetDateTimes implements ObjectSubstitution<OffsetDateTime, String> {
        public String serialize(OffsetDateTime obj) {
            return obj.toString();
        }

        public OffsetDateTime deserialize(String obj) {
            return OffsetDateTime.parse(obj);
        }
    }

    public static class ZonedDateTimes implements ObjectSubstitution<ZonedDateTime, String> {
        public String serialize(ZonedDateTime obj) {
            return obj.toString();
        }

        public ZonedDateTime deserialize(String obj) {
            return ZonedDateTime.parse(obj);
        }
    }
}
