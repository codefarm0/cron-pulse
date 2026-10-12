package in.codefarm.cronpulse.ping;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import in.codefarm.cronpulse.config.CronPulseProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * A token bucket per check (Bucket4j), kept in this instance's memory.
 * <p>
 * Each check gets a bucket of N tokens that refills at N tokens per second; a ping takes one.
 * A job may send a short burst, but not more than N pings per second for long.
 * <p>
 * Buckets live in a bounded cache and are dropped after a minute without pings, so checks that
 * pinged once don't keep memory forever. A dropped bucket comes back full, which is the same as
 * a bucket that has been refilling for a minute.
 * <p>
 * Limitation: with three instances behind a load balancer, a check can send up to 3 × N pings per
 * second. Phase 5 replaces this class with a Redis-backed one.
 */
@Component
class InMemoryPingRateLimiter implements PingRateLimiter {

    private final Cache<Long, Bucket> buckets;
    private final int perSecond;
    private final TimeMeter timeMeter;

    InMemoryPingRateLimiter(CronPulseProperties properties, Clock clock) {
        this.perSecond = properties.ping().rateLimitPerSecond();
        this.timeMeter = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.ping().lookupCacheSize())
                .expireAfterAccess(Duration.ofMinutes(1))
                .build();
    }

    @Override
    public Decision tryAcquire(long checkId) {
        var probe = buckets.get(checkId, id -> newBucket()).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.ALLOWED;
        }
        return Decision.rejected(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perSecond).refillGreedy(perSecond, Duration.ofSeconds(1)))
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    /** Bucket4j reads time from our {@link Clock}, like the rest of the app, so tests can control it. */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            var now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000L + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
