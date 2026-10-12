package in.codefarm.cronpulse.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.codefarm.cronpulse.TestcontainersConfiguration;
import in.codefarm.cronpulse.tenant.Organization;
import in.codefarm.cronpulse.tenant.OrganizationRepository;
import in.codefarm.cronpulse.tenant.Plan;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class CheckRepositoryTest {

    static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    @Autowired
    CheckRepository checks;

    @Autowired
    OrganizationRepository organizations;

    Organization acme;
    Organization globex;

    @BeforeEach
    void createTenants() {
        acme = organizations.save(new Organization("Acme", "acme", Plan.FREE, NOW));
        globex = organizations.save(new Organization("Globex", "globex", Plan.PRO, NOW));
    }

    @Test
    void storesAndReadsBackACronCheck() {
        var saved = checks.saveAndFlush(new Check(acme.getId(), "Nightly DB backup", "nightly-db-backup",
                List.of("db", "prod"), Schedule.cron("0 2 * * *", "Asia/Kolkata"), Duration.ofMinutes(15), NOW));

        var found = checks.findByOrganizationIdAndPublicId(acme.getId(), saved.getPublicId()).orElseThrow();

        assertThat(found.getTags()).containsExactly("db", "prod");
        assertThat(found.getSchedule()).isEqualTo(Schedule.cron("0 2 * * *", "Asia/Kolkata"));
        assertThat(found.getGrace()).isEqualTo(Duration.ofMinutes(15));
        assertThat(found.getStatus()).isEqualTo(CheckStatus.NEW);
        assertThat(found.getPingUuid()).isNotEqualTo(found.getPublicId());
    }

    @Test
    void neverReturnsAnotherTenantsCheck() {
        var acmeCheck = checks.saveAndFlush(hourly(acme, "backup"));

        assertThat(checks.findByOrganizationIdAndPublicId(globex.getId(), acmeCheck.getPublicId())).isEmpty();
    }

    @Test
    void slugIsUniqueWithinAnOrganisationOnly() {
        checks.saveAndFlush(hourly(acme, "backup"));

        // Another tenant can use the same slug...
        checks.saveAndFlush(hourly(globex, "backup"));
        assertThat(checks.existsByOrganizationIdAndSlug(globex.getId(), "backup")).isTrue();

        // ...but the same tenant cannot.
        assertThatThrownBy(() -> checks.saveAndFlush(hourly(acme, "backup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAScheduleWithBothPeriodAndCron() {
        var inconsistent = new Schedule(ScheduleType.SIMPLE, 3600, "0 * * * *", "UTC");

        assertThatThrownBy(() -> checks.saveAndFlush(new Check(acme.getId(), "Broken", "broken", List.of(),
                inconsistent, Duration.ZERO, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("checks_schedule_consistent");
    }

    @Test
    void findsACheckByItsPingUuid() {
        var saved = checks.saveAndFlush(hourly(acme, "backup"));

        assertThat(checks.findIdByPingUuid(saved.getPingUuid())).contains(saved.getId());
        assertThat(checks.findIdByPingUuid(UUID.randomUUID())).isEmpty();
    }

    @Test
    void versionIncreasesOnEveryUpdate() {
        var saved = checks.saveAndFlush(hourly(acme, "backup"));
        long before = saved.getVersion();

        saved.update("Renamed", "backup", List.of(), Schedule.every(Duration.ofHours(2)), Duration.ZERO, NOW);
        checks.saveAndFlush(saved);

        assertThat(saved.getVersion()).isEqualTo(before + 1);
    }

    private static Check hourly(Organization org, String slug) {
        return new Check(org.getId(), slug, slug, List.of(), Schedule.every(Duration.ofHours(1)), Duration.ofMinutes(5), NOW);
    }
}
