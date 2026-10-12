package in.codefarm.cronpulse.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the {@code cronpulse.*} prefix.
 *
 * @param publicBaseUrl             the URL users and jobs reach CronPulse on; ping URLs are built from it
 * @param defaultOrganizationSlug   the organisation every request uses until login exists (Phase 4)
 * @param ping                      settings for the ping ingest endpoint
 */
@ConfigurationProperties("cronpulse")
public record CronPulseProperties(
        @DefaultValue("http://localhost:8080") URI publicBaseUrl,
        @DefaultValue("default") String defaultOrganizationSlug,
        @DefaultValue Ping ping) {

    /**
     * @param rateLimitPerSecond  pings a single check may send per second before getting 429
     * @param lookupCacheTtl      how long a {@code ping_uuid → check} lookup is cached
     * @param lookupCacheSize     how many lookups are cached at most (one entry is a few dozen bytes)
     */
    public record Ping(
            @DefaultValue("5") int rateLimitPerSecond,
            @DefaultValue("5m") Duration lookupCacheTtl,
            @DefaultValue("100000") long lookupCacheSize) {
    }
}
