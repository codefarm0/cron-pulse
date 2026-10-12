package in.codefarm.cronpulse.ping;

public enum PingType {
    /** The job finished successfully. */
    SUCCESS,
    /** The job started; the next SUCCESS or FAIL ping measures its duration. */
    START,
    /** The job reported a failure. */
    FAIL
}
