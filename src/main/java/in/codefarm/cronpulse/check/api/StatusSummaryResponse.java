package in.codefarm.cronpulse.check.api;

import in.codefarm.cronpulse.check.CheckStatus;
import java.util.Map;

/** Check counts per status, for dashboards. Every status is present, with 0 if there are none. */
public record StatusSummaryResponse(long total, Map<CheckStatus, Long> byStatus) {
}
