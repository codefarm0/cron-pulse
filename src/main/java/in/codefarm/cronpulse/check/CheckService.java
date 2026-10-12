package in.codefarm.cronpulse.check;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for managing checks. The REST API calls this today; the Thymeleaf UI calls the same
 * methods in Phase 4, so behaviour can never differ between the two.
 * <p>
 * Every method takes the caller's organisation id, and every lookup is scoped by it.
 */
@Service
@Transactional
public class CheckService {

    private final CheckRepository checks;
    private final CheckStateMachine stateMachine;
    private final Clock clock;

    CheckService(CheckRepository checks, CheckStateMachine stateMachine, Clock clock) {
        this.checks = checks;
        this.stateMachine = stateMachine;
        this.clock = clock;
    }

    public Check create(long organizationId, CheckDetails details) {
        ScheduleRules.validate(details.schedule(), details.grace());
        var slug = slugOrDefault(details);
        if (checks.existsByOrganizationIdAndSlug(organizationId, slug)) {
            throw new SlugTakenException(slug);
        }
        var check = new Check(organizationId, details.name(), slug, details.tags(),
                details.schedule(), details.grace(), clock.instant());
        return saveCatchingSlugRace(check, slug);
    }

    @Transactional(readOnly = true)
    public Check get(long organizationId, UUID publicId) {
        return checks.findByOrganizationIdAndPublicId(organizationId, publicId)
                .orElseThrow(() -> new CheckNotFoundException(publicId));
    }

    @Transactional(readOnly = true)
    public Page<Check> list(long organizationId, CheckFilter filter, Pageable pageable) {
        var status = filter.status() == null ? null : filter.status().name();
        var search = blankToNull(filter.search());
        return checks.search(organizationId, status, blankToNull(filter.tag()),
                search == null ? null : escapeLike(search), pageable);
    }

    /**
     * @param expectedVersion the version the client last saw (from the ETag), or null to skip the check
     */
    public Check update(long organizationId, UUID publicId, CheckDetails details, Long expectedVersion) {
        var check = get(organizationId, publicId);
        if (expectedVersion != null && expectedVersion != check.getVersion()) {
            throw new StaleCheckException(expectedVersion, check.getVersion());
        }
        ScheduleRules.validate(details.schedule(), details.grace());
        var slug = slugOrDefault(details);
        if (!slug.equals(check.getSlug()) && checks.existsByOrganizationIdAndSlug(organizationId, slug)) {
            throw new SlugTakenException(slug);
        }
        check.update(details.name(), slug, details.tags(), details.schedule(), details.grace(), clock.instant());
        return saveCatchingSlugRace(check, slug);
    }

    public void delete(long organizationId, UUID publicId) {
        checks.delete(get(organizationId, publicId));
    }

    public Check pause(long organizationId, UUID publicId) {
        var check = get(organizationId, publicId);
        stateMachine.pause(check, "Paused via API");
        return check;
    }

    public Check resume(long organizationId, UUID publicId) {
        var check = get(organizationId, publicId);
        stateMachine.resume(check, "Resumed via API");
        return check;
    }

    /** Number of checks in each status, including statuses with zero checks. */
    @Transactional(readOnly = true)
    public Map<CheckStatus, Long> countByStatus(long organizationId) {
        var counts = new EnumMap<CheckStatus, Long>(CheckStatus.class);
        for (var status : CheckStatus.values()) {
            counts.put(status, 0L);
        }
        for (var row : checks.countByStatus(organizationId)) {
            counts.put(row.status(), row.total());
        }
        return counts;
    }

    private static String slugOrDefault(CheckDetails details) {
        return details.slug() != null && !details.slug().isBlank() ? details.slug() : Slugs.fromName(details.name());
    }

    /**
     * The exists-check above can race with a concurrent request creating the same slug.
     * The unique constraint is the real guarantee; this turns its violation into a clear error.
     */
    private Check saveCatchingSlugRace(Check check, String slug) {
        try {
            return checks.saveAndFlush(check);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("checks_org_slug_uq")) {
                throw new SlugTakenException(slug);
            }
            throw e;
        }
    }

    /** A search for "50%" must match the text "50%", not "50 followed by anything". */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
