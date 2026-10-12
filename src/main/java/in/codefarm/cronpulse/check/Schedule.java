package in.codefarm.cronpulse.check;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Duration;
import java.util.Objects;

/**
 * When a check expects its pings: either a fixed period or a cron expression in a time zone.
 * <p>
 * A value object stored inline in the {@code checks} table. The factory methods make an
 * inconsistent schedule (both a period and a cron expression) impossible to build in Java;
 * a CHECK constraint enforces the same rule in the database.
 */
@Embeddable
public record Schedule(
        @Enumerated(EnumType.STRING) @Column(name = "schedule_type", nullable = false) ScheduleType type,
        @Column(name = "period_seconds") Integer periodSeconds,
        @Column(name = "cron_expression") String cronExpression,
        @Column(name = "time_zone", nullable = false) String timeZone) {

    public Schedule {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timeZone, "timeZone");
    }

    public static Schedule every(Duration period) {
        return new Schedule(ScheduleType.SIMPLE, Math.toIntExact(period.toSeconds()), null, "UTC");
    }

    public static Schedule cron(String expression, String timeZone) {
        return new Schedule(ScheduleType.CRON, null, expression, timeZone);
    }
}
