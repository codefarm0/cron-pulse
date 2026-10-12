package in.codefarm.cronpulse;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the system clock with a {@link MutableClock} for tests that need to control time. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    public static final Instant START = Instant.parse("2026-10-12T10:00:00Z");

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(START);
    }
}
