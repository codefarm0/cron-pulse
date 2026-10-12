package in.codefarm.cronpulse.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "now" for the whole application.
 * <p>
 * Code asks this {@link Clock} for the time instead of calling {@code Instant.now()}, so tests can
 * replace it with a fixed or manually advanced clock and check time-based behaviour exactly.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
