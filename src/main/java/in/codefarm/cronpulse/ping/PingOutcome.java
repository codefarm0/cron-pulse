package in.codefarm.cronpulse.ping;

import java.time.Duration;

/** What happened to a ping. A sealed type, so the controller's switch must handle every case. */
public sealed interface PingOutcome {

    record Recorded(long pingId) implements PingOutcome {
    }

    record UnknownCheck() implements PingOutcome {
    }

    record RateLimited(Duration retryAfter) implements PingOutcome {
    }
}
