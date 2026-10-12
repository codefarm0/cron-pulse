package in.codefarm.cronpulse.api;

/**
 * Stable, machine-readable error codes returned in every error response as {@code "code"}.
 * Clients branch on these, never on the human-readable {@code detail} text, which may change.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    CHECK_NOT_FOUND,
    SLUG_TAKEN,
    PRECONDITION_FAILED,
    CONCURRENT_MODIFICATION,
    IDEMPOTENCY_KEY_REUSED
}
