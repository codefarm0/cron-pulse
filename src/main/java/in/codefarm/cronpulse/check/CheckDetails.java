package in.codefarm.cronpulse.check;

import java.time.Duration;
import java.util.List;

/**
 * The user-editable fields of a check, as the service receives them from the API or (later) the UI.
 *
 * @param slug may be null; it is then derived from the name
 */
public record CheckDetails(String name, String slug, List<String> tags, Schedule schedule, Duration grace) {
}
