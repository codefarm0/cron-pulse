package in.codefarm.cronpulse.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the {@code cronpulse.*} prefix.
 *
 * @param publicBaseUrl             the URL users and jobs reach CronPulse on; ping URLs are built from it
 * @param defaultOrganizationSlug   the organisation every request uses until login exists (Phase 4)
 */
@ConfigurationProperties("cronpulse")
public record CronPulseProperties(
        @DefaultValue("http://localhost:8080") URI publicBaseUrl,
        @DefaultValue("default") String defaultOrganizationSlug) {
}
