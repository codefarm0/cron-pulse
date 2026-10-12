package in.codefarm.cronpulse.check.api;

import in.codefarm.cronpulse.check.Check;
import in.codefarm.cronpulse.check.StaleCheckException;

/**
 * The ETag of a check is its optimistic-locking version, e.g. {@code "3"}.
 * <p>
 * A client that sends {@code If-Match: "3"} on update is saying "only apply this if nobody changed
 * the check since I read version 3". Without it, the last writer silently wins.
 */
final class ETags {

    private ETags() {
    }

    static String of(Check check) {
        return "\"" + check.getVersion() + "\"";
    }

    /** @return the version in an If-Match header, or null if the header is absent or {@code *} */
    static Long parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.trim().equals("*")) {
            return null;
        }
        var value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // An ETag we never issued can't match the current version.
            throw new StaleCheckException(-1, -1);
        }
    }
}
