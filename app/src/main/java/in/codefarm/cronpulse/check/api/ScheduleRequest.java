package in.codefarm.cronpulse.check.api;

import in.codefarm.cronpulse.check.Schedule;
import in.codefarm.cronpulse.check.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Either a SIMPLE period or a CRON expression in a time zone.")
public record ScheduleRequest(
        @NotNull ScheduleType type,
        @Schema(description = "SIMPLE only: seconds between pings (60 to 31536000)", example = "3600")
        @Positive Integer periodSeconds,
        @Schema(description = "CRON only: classic 5-field cron expression", example = "0 2 * * *")
        @Size(max = 100) String cron,
        @Schema(description = "IANA time zone for CRON schedules; defaults to UTC", example = "Asia/Kolkata")
        @Size(max = 64) String timeZone) {

    Schedule toSchedule() {
        var zone = timeZone == null || timeZone.isBlank() ? "UTC" : timeZone;
        return new Schedule(type, periodSeconds, cron == null ? null : cron.trim(), zone);
    }
}
