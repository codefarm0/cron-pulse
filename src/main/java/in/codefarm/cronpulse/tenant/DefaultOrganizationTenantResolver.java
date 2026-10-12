package in.codefarm.cronpulse.tenant;

import in.codefarm.cronpulse.config.CronPulseProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 1 stand-in for real tenancy: every request acts for one configured organisation,
 * which is created on startup if it doesn't exist. Replaced in Phase 4 by a resolver
 * that reads the logged-in user or API key.
 */
@Component
class DefaultOrganizationTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultOrganizationTenantResolver.class);

    private final OrganizationRepository organizations;
    private final CronPulseProperties properties;
    private final Clock clock;

    private volatile Long organizationId;

    DefaultOrganizationTenantResolver(OrganizationRepository organizations, CronPulseProperties properties, Clock clock) {
        this.organizations = organizations;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    void ensureDefaultOrganizationExists() {
        currentOrganizationId();
    }

    @Override
    public long currentOrganizationId() {
        Long id = organizationId;
        if (id == null) {
            String slug = properties.defaultOrganizationSlug();
            id = organizations.findBySlug(slug)
                    .orElseGet(() -> {
                        log.info("Creating default organisation '{}'", slug);
                        return organizations.save(new Organization("Default", slug, Plan.FREE, clock.instant()));
                    })
                    .getId();
            organizationId = id;
        }
        return id;
    }
}
