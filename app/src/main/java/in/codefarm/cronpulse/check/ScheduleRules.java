package in.codefarm.cronpulse.check;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.scheduling.support.CronExpression;

/**
 * Validates schedules before they are stored. Full schedule maths (next run times, previews)
 * is built in Phase 2; here we only make sure nothing invalid gets into the database.
 */
public final class ScheduleRules {

    public static final Duration MIN_PERIOD = Duration.ofMinutes(1);
    public static final Duration MAX_PERIOD = Duration.ofDays(365);
    public static final Duration MAX_GRACE = Duration.ofDays(7);

    private ScheduleRules() {
    }

    /** @throws InvalidScheduleException with a message suitable for API clients */
    public static void validate(Schedule schedule, Duration grace) {
        try {
            ZoneId.of(schedule.timeZone());
        } catch (DateTimeException e) {
            throw new InvalidScheduleException("timeZone", "Unknown time zone '" + schedule.timeZone() + "'. Use an IANA id such as Asia/Kolkata.");
        }
        if (grace.isNegative() || grace.compareTo(MAX_GRACE) > 0) {
            throw new InvalidScheduleException("graceSeconds", "Grace period must be between 0 and 7 days.");
        }
        switch (schedule.type()) {
            case SIMPLE -> validatePeriod(schedule);
            case CRON -> validateCron(schedule);
        }
    }

    private static void validatePeriod(Schedule schedule) {
        if (schedule.periodSeconds() == null || schedule.cronExpression() != null) {
            throw new InvalidScheduleException("schedule", "A SIMPLE schedule needs periodSeconds and no cron expression.");
        }
        var period = Duration.ofSeconds(schedule.periodSeconds());
        if (period.compareTo(MIN_PERIOD) < 0 || period.compareTo(MAX_PERIOD) > 0) {
            throw new InvalidScheduleException("schedule.periodSeconds", "Period must be between 60 seconds and 365 days.");
        }
    }

    private static void validateCron(Schedule schedule) {
        var expression = schedule.cronExpression();
        if (expression == null || schedule.periodSeconds() != null) {
            throw new InvalidScheduleException("schedule", "A CRON schedule needs a cron expression and no periodSeconds.");
        }
        // Users write classic 5-field cron (minute hour day month weekday). Spring's parser expects
        // a leading seconds field, so we add "0" before parsing. Phase 2 reuses this conversion.
        if (expression.trim().split("\\s+").length != 5) {
            throw new InvalidScheduleException("schedule.cron", "Use a 5-field cron expression, e.g. '0 2 * * *'.");
        }
        try {
            CronExpression.parse("0 " + expression.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidScheduleException("schedule.cron", "Invalid cron expression: " + e.getMessage());
        }
    }
}
