package in.codefarm.cronpulse.ping;

import static org.assertj.core.api.Assertions.assertThat;

import in.codefarm.cronpulse.MutableClock;
import in.codefarm.cronpulse.config.CronPulseProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryPingRateLimiterTest {

    final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));
    final InMemoryPingRateLimiter limiter = new InMemoryPingRateLimiter(new CronPulseProperties(
            URI.create("http://localhost"), "default",
            new CronPulseProperties.Ping(5, Duration.ofMinutes(5), 1000)), clock);

    @Test
    void allowsABurstUpToTheLimitThenRejects() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(1).allowed()).isTrue();
        }

        var rejected = limiter.tryAcquire(1);

        assertThat(rejected.allowed()).isFalse();
        // 5 tokens per second refill one token every 200 ms.
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void refillsAsTimePasses() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(1);
        }

        clock.advance(Duration.ofMillis(200));

        assertThat(limiter.tryAcquire(1).allowed()).isTrue();
        assertThat(limiter.tryAcquire(1).allowed()).isFalse();
    }

    @Test
    void eachCheckHasItsOwnLimit() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(1);
        }

        assertThat(limiter.tryAcquire(1).allowed()).isFalse();
        assertThat(limiter.tryAcquire(2).allowed()).isTrue();
    }
}
