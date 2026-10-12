package in.codefarm.cronpulse.ping;

import java.time.Duration;

/**
 * Limits how often one check may be pinged, to protect the database from a job stuck in a loop.
 * <p>
 * An interface because the first implementation keeps its counters in memory, which only works on
 * a single instance. Phase 5 adds a Redis-backed implementation so the limit holds across instances.
 */
public interface PingRateLimiter {

    Decision tryAcquire(long checkId);

    /** @param retryAfter how long the caller should wait before trying again; zero when allowed */
    record Decision(boolean allowed, Duration retryAfter) {

        static final Decision ALLOWED = new Decision(true, Duration.ZERO);

        static Decision rejected(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }
}
