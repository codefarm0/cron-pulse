package in.codefarm.cronpulse.api.idempotency;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("This Idempotency-Key was already used with a different request body. Use a new key for a new request.");
    }
}
