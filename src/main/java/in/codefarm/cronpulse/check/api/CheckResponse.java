package in.codefarm.cronpulse.check.api;

import in.codefarm.cronpulse.check.Check;
import in.codefarm.cronpulse.check.CheckStatus;
import in.codefarm.cronpulse.check.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A check as the API shows it. Built from the entity by {@link #from}; the entity itself is never
 * serialised, so renaming a column or adding an internal field can't change the API.
 */
public record CheckResponse(
        @Schema(description = "Public id used in API URLs") UUID id,
        String name,
        String slug,
        List<String> tags,
        ScheduleResponse schedule,
        int graceSeconds,
        CheckStatus status,
        @Schema(description = "The URL your job calls. Treat it as a secret.") URI pingUrl,
        Instant lastPingAt,
        Instant nextExpectedAt,
        Instant pausedAt,
        Instant createdAt,
        Instant updatedAt) {

    public record ScheduleResponse(ScheduleType type, Integer periodSeconds, String cron, String timeZone) {
    }

    static CheckResponse from(Check check, URI publicBaseUrl) {
        var schedule = check.getSchedule();
        return new CheckResponse(
                check.getPublicId(),
                check.getName(),
                check.getSlug(),
                check.getTags(),
                new ScheduleResponse(schedule.type(), schedule.periodSeconds(), schedule.cronExpression(), schedule.timeZone()),
                (int) check.getGrace().toSeconds(),
                check.getStatus(),
                publicBaseUrl.resolve("/ping/" + check.getPingUuid()),
                check.getLastPingAt(),
                check.getNextExpectedAt(),
                check.getPausedAt(),
                check.getCreatedAt(),
                check.getUpdatedAt());
    }
}
