package in.codefarm.cronpulse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs. Every job must be safe to run on several instances at once. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
