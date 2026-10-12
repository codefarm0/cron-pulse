package in.codefarm.cronpulse.check;

/** The client edited an older version of the check than the one stored (optimistic locking). */
public class StaleCheckException extends RuntimeException {

    public StaleCheckException(long expectedVersion, long actualVersion) {
        super("The check was modified (version " + actualVersion + ") since you read it (version "
                + expectedVersion + "). Fetch it again and reapply your change.");
    }
}
