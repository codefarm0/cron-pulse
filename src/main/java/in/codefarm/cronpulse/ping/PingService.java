package in.codefarm.cronpulse.ping;

import in.codefarm.cronpulse.check.Check;
import in.codefarm.cronpulse.check.CheckRepository;
import in.codefarm.cronpulse.check.CheckStateMachine;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records pings: the hot path of the whole service.
 * <p>
 * The order of the steps is the design:
 * <ol>
 *   <li>look up the check (usually from the cache, no database);</li>
 *   <li>apply the rate limit (in memory, no database);</li>
 *   <li>read the body (network I/O, still no database);</li>
 *   <li>only then open a short transaction: lock the check, insert the ping, apply it.</li>
 * </ol>
 * Cheap rejections come first, and the database connection is held only for the few milliseconds
 * the writes take, never while a slow client is still uploading its output. No alert is ever sent
 * from here; alerts are queued and delivered in the background (Phase 3).
 * <p>
 * Not {@code @Transactional} on purpose: a class-level transaction would hold a connection for the
 * whole method, including the body read. {@link TransactionTemplate} wraps only the writes.
 */
@Service
public class PingService {

    private final CheckLookup lookup;
    private final PingRateLimiter rateLimiter;
    private final CheckRepository checks;
    private final PingRepository pings;
    private final CheckStateMachine stateMachine;
    private final TransactionTemplate transaction;
    private final Clock clock;

    PingService(CheckLookup lookup, PingRateLimiter rateLimiter, CheckRepository checks, PingRepository pings,
            CheckStateMachine stateMachine, TransactionTemplate transaction, Clock clock) {
        this.lookup = lookup;
        this.rateLimiter = rateLimiter;
        this.checks = checks;
        this.pings = pings;
        this.stateMachine = stateMachine;
        this.transaction = transaction;
        this.clock = clock;
    }

    public PingOutcome record(UUID pingUuid, PingSignal signal, PingSource source, InputStream body)
            throws IOException {
        var receivedAt = clock.instant();
        var checkId = lookup.checkIdFor(pingUuid);
        if (checkId.isEmpty()) {
            return new PingOutcome.UnknownCheck();
        }
        var decision = rateLimiter.tryAcquire(checkId.get());
        if (!decision.allowed()) {
            return new PingOutcome.RateLimited(decision.retryAfter());
        }
        var output = PingBody.read(body);
        return transaction.execute(status -> save(pingUuid, checkId.get(), signal, source, output, receivedAt));
    }

    private PingOutcome save(UUID pingUuid, long checkId, PingSignal signal, PingSource source, PingBody output,
            Instant receivedAt) {
        var check = checks.findByIdForUpdate(checkId).orElse(null);
        if (check == null) {
            // Deleted after its id was cached (possibly on another instance).
            lookup.evict(pingUuid);
            return new PingOutcome.UnknownCheck();
        }
        var ping = pings.save(new Ping(checkId, signal.type(), signal.exitCode(), receivedAt,
                durationMs(check, signal.type(), receivedAt), source.ip(), source.userAgent(), source.method(),
                output.text(), output.truncated()));
        stateMachine.applyPing(check, signal.type(), receivedAt);
        return new PingOutcome.Recorded(ping.getId());
    }

    /**
     * How long the job ran: the time since its START ping, if that START hasn't already been
     * closed by an earlier SUCCESS or FAIL.
     */
    private static Long durationMs(Check check, PingType type, Instant receivedAt) {
        var startedAt = check.getLastStartAt();
        if (type == PingType.START || startedAt == null) {
            return null;
        }
        var lastFinishedAt = check.getLastPingAt();
        if (lastFinishedAt != null && !startedAt.isAfter(lastFinishedAt)) {
            return null;
        }
        // Instances' clocks can differ by a few milliseconds, so "start" may look later than "now".
        return Math.max(0, Duration.between(startedAt, receivedAt).toMillis());
    }
}
