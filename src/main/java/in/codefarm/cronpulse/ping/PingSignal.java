package in.codefarm.cronpulse.ping;

/**
 * What a ping says about the job, worked out from the URL it was sent to.
 *
 * @param exitCode the job's exit code when the URL carried one ({@code /ping/{uuid}/{exitCode}}), else null
 */
public record PingSignal(PingType type, Integer exitCode) {

    public static final int MAX_EXIT_CODE = 255;

    public static PingSignal success() {
        return new PingSignal(PingType.SUCCESS, null);
    }

    public static PingSignal start() {
        return new PingSignal(PingType.START, null);
    }

    public static PingSignal fail() {
        return new PingSignal(PingType.FAIL, null);
    }

    /** Follows the Unix convention: 0 means success, anything else (1–255) means failure. */
    public static PingSignal exitCode(int exitCode) {
        if (exitCode < 0 || exitCode > MAX_EXIT_CODE) {
            throw new IllegalArgumentException("Exit code must be between 0 and " + MAX_EXIT_CODE);
        }
        return new PingSignal(exitCode == 0 ? PingType.SUCCESS : PingType.FAIL, exitCode);
    }
}
