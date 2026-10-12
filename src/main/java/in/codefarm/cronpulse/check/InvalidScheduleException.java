package in.codefarm.cronpulse.check;

/** A schedule that can't be stored. {@code field} names the request field to blame. */
public class InvalidScheduleException extends RuntimeException {

    private final String field;

    public InvalidScheduleException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
