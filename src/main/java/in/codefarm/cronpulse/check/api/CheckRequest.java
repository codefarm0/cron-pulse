package in.codefarm.cronpulse.check.api;

import in.codefarm.cronpulse.check.CheckDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;

/** Body of POST and PUT /api/v1/checks. PUT replaces all editable fields. */
public record CheckRequest(
        @Schema(example = "Nightly DB backup")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "URL-friendly id, unique in your organisation. Derived from the name if omitted.", example = "nightly-db-backup")
        @Size(max = 60) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters, digits and single hyphens")
        String slug,

        @Schema(example = "[\"prod\", \"postgres\"]")
        @Size(max = 10) List<@NotBlank @Size(max = 30) @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*$",
                message = "must be lowercase letters, digits, '.', '_' or '-'") String> tags,

        @NotNull @Valid ScheduleRequest schedule,

        @Schema(description = "How late a ping may be before the check goes DOWN, in seconds (max 7 days)", example = "900")
        @NotNull @Min(0) @Max(604_800) Integer graceSeconds) {

    CheckDetails toDetails() {
        return new CheckDetails(name.trim(), slug, tags == null ? List.of() : List.copyOf(tags),
                schedule.toSchedule(), Duration.ofSeconds(graceSeconds));
    }
}
