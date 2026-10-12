package in.codefarm.cronpulse.ping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.codefarm.cronpulse.TestcontainersConfiguration;
import in.codefarm.cronpulse.check.Check;
import in.codefarm.cronpulse.check.CheckRepository;
import in.codefarm.cronpulse.check.Schedule;
import in.codefarm.cronpulse.tenant.Organization;
import in.codefarm.cronpulse.tenant.OrganizationRepository;
import in.codefarm.cronpulse.tenant.Plan;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class PingRepositoryTest {

    static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    @Autowired
    PingRepository pings;

    @Autowired
    CheckRepository checks;

    @Autowired
    OrganizationRepository organizations;

    Check check;

    @BeforeEach
    void createCheck() {
        var org = organizations.save(new Organization("Acme", "acme", Plan.FREE, NOW));
        check = checks.save(new Check(org.getId(), "Backup", "backup", List.of(),
                Schedule.every(Duration.ofHours(1)), Duration.ofMinutes(5), NOW));
    }

    @Test
    void pingLogIsNewestFirstAndPaged() throws Exception {
        for (int minutesAgo = 0; minutesAgo < 5; minutesAgo++) {
            pings.save(success(NOW.minus(Duration.ofMinutes(minutesAgo)), null));
        }

        var page = pings.findByCheckIdOrderByReceivedAtDesc(check.getId(), PageRequest.of(0, 3));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).extracting(Ping::getReceivedAt)
                .containsExactly(NOW, NOW.minus(Duration.ofMinutes(1)), NOW.minus(Duration.ofMinutes(2)));
    }

    @Test
    void storesSourceIpAsInet() throws Exception {
        var saved = pings.saveAndFlush(success(NOW, InetAddress.getByName("2001:db8::1")));

        assertThat(pings.findById(saved.getId()).orElseThrow().getSourceIp())
                .isEqualTo(InetAddress.getByName("2001:db8::1"));
    }

    @Test
    void databaseRejectsABodyOverTenKilobytes() {
        var oversized = "x".repeat(10 * 1024 + 1);

        assertThatThrownBy(() -> pings.saveAndFlush(new Ping(check.getId(), PingType.SUCCESS, null, NOW, null,
                null, "curl/8", "POST", oversized, false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Ping success(Instant at, InetAddress ip) {
        return new Ping(check.getId(), PingType.SUCCESS, 0, at, null, ip, "curl/8", "GET", null, false);
    }
}
