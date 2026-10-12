package in.codefarm.cronpulse.ping;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import in.codefarm.cronpulse.MutableClock;
import in.codefarm.cronpulse.TestClockConfiguration;
import in.codefarm.cronpulse.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end tests of /ping/**: real Spring context and Postgres, with a clock the test controls
 * (the rate limiter and job durations both depend on time).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class PingApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MutableClock clock;

    String checkId;
    String pingPath;

    @BeforeEach
    void createCheck() {
        jdbc.sql("DELETE FROM idempotency_keys").update();
        jdbc.sql("DELETE FROM checks").update();
        clock.set(TestClockConfiguration.START);
        var created = body(mvc.post().uri("/api/v1/checks").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Nightly backup", "schedule": {"type": "SIMPLE", "periodSeconds": 3600}, "graceSeconds": 300}
                """).exchange());
        checkId = created.get("id").asString();
        pingPath = java.net.URI.create(created.get("pingUrl").asString()).getPath();
    }

    @Nested
    class Success {

        @Test
        void getRecordsASuccessPingAndAnswersOk() {
            var result = mvc.get().uri(pingPath).header(HttpHeaders.USER_AGENT, "curl/8.7.1").exchange();

            assertThat(result).hasStatusOk().hasBodyTextEqualTo("OK");
            assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_PLAIN_VALUE);
            var ping = onlyPing();
            assertThat(ping.type()).isEqualTo("SUCCESS");
            assertThat(ping.method()).isEqualTo("GET");
            assertThat(ping.userAgent()).isEqualTo("curl/8.7.1");
            assertThat(ping.sourceIp()).isEqualTo("127.0.0.1");
            assertThat(ping.receivedAt()).isEqualTo(TestClockConfiguration.START);
            assertThat(ping.body()).isNull();
        }

        @Test
        void updatesTheChecksLastPingTime() {
            mvc.get().uri(pingPath).exchange();

            assertThat(check().get("lastPingAt").asString()).isEqualTo("2026-10-12T10:00:00Z");
        }

        @Test
        void headIsAcceptedAndRecordedAsHead() {
            assertThat(mvc.method(HttpMethod.HEAD).uri(pingPath).exchange()).hasStatusOk();

            assertThat(onlyPing().method()).isEqualTo("HEAD");
        }

        @Test
        void postStoresTheJobOutput() {
            mvc.post().uri(pingPath).contentType(MediaType.TEXT_PLAIN).content("dumped 42 tables").exchange();

            assertThat(onlyPing().body()).isEqualTo("dumped 42 tables");
        }

        @Test
        void formEncodedOutputIsStoredVerbatim() {
            // `curl -d` sends this content type by default; the body must not be parsed as form fields.
            mvc.post().uri(pingPath).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content("rows=42&status=ok").exchange();

            assertThat(onlyPing().body()).isEqualTo("rows=42&status=ok");
        }

        @Test
        void oversizedOutputIsTruncatedToTenKilobytes() {
            var result = mvc.post().uri(pingPath).contentType(MediaType.TEXT_PLAIN)
                    .content("x".repeat(50 * 1024)).exchange();

            assertThat(result).hasStatusOk();
            var ping = onlyPing();
            assertThat(ping.body().getBytes(UTF_8)).hasSize(10 * 1024);
            assertThat(ping.bodyTruncated()).isTrue();
        }

        @Test
        void doesNotChangeTheChecksETag() {
            var etagBefore = mvc.get().uri("/api/v1/checks/{id}", checkId).exchange()
                    .getResponse().getHeader(HttpHeaders.ETAG);

            mvc.get().uri(pingPath).exchange();

            // Pings are frequent; if they bumped the version, a user's edit would fail with 412.
            assertThat(mvc.get().uri("/api/v1/checks/{id}", checkId).exchange().getResponse()
                    .getHeader(HttpHeaders.ETAG)).isEqualTo(etagBefore);
        }
    }

    @Nested
    class Signals {

        @Test
        void failRecordsAFailPing() {
            mvc.get().uri(pingPath + "/fail").exchange();

            var ping = onlyPing();
            assertThat(ping.type()).isEqualTo("FAIL");
            assertThat(ping.exitCode()).isNull();
        }

        @ParameterizedTest
        @CsvSource({"0, SUCCESS", "1, FAIL", "2, FAIL", "137, FAIL", "255, FAIL"})
        void exitCodeZeroIsSuccessAnythingElseIsFailure(int exitCode, String type) {
            assertThat(mvc.get().uri(pingPath + "/" + exitCode).exchange()).hasStatusOk();

            var ping = onlyPing();
            assertThat(ping.type()).isEqualTo(type);
            assertThat(ping.exitCode()).isEqualTo(exitCode);
        }

        @ParameterizedTest
        @CsvSource({"256", "1000", "99999999999"})
        void exitCodesAbove255AreRejected(String exitCode) {
            assertThat(mvc.get().uri(pingPath + "/" + exitCode).exchange()).hasStatus(400);
            assertThat(pingCount()).isZero();
        }

        @Test
        void startThenSuccessRecordsTheJobsDuration() {
            mvc.get().uri(pingPath + "/start").exchange();
            clock.advance(Duration.ofSeconds(90));
            mvc.get().uri(pingPath).exchange();

            assertThat(pings()).extracting(PingRow::type, PingRow::durationMs)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("START", null),
                            org.assertj.core.groups.Tuple.tuple("SUCCESS", 90_000L));
        }

        @Test
        void aStartIsOnlyUsedOnce() {
            mvc.get().uri(pingPath + "/start").exchange();
            clock.advance(Duration.ofSeconds(10));
            mvc.get().uri(pingPath + "/fail").exchange();
            clock.advance(Duration.ofSeconds(10));
            mvc.get().uri(pingPath).exchange();

            assertThat(pings()).extracting(PingRow::durationMs).containsExactly(null, 10_000L, null);
        }

        @Test
        void startDoesNotCountAsTheLastPing() {
            mvc.get().uri(pingPath + "/start").exchange();

            assertThat(check().get("lastPingAt").isNull()).isTrue();
        }
    }

    @Nested
    class Rejections {

        @Test
        void unknownUuidIs404() {
            assertThat(mvc.get().uri("/ping/{uuid}", UUID.randomUUID()).exchange()).hasStatus(404);
        }

        @Test
        void malformedUuidIs404() {
            assertThat(mvc.get().uri("/ping/not-a-uuid").exchange()).hasStatus(404);
        }

        @Test
        void unknownSubPathIs404() {
            assertThat(mvc.get().uri(pingPath + "/finished").exchange()).hasStatus(404);
            assertThat(pingCount()).isZero();
        }

        @Test
        void deletedCheckIs404EvenAfterItsIdWasCached() {
            mvc.get().uri(pingPath).exchange();

            mvc.delete().uri("/api/v1/checks/{id}", checkId).exchange();

            assertThat(mvc.get().uri(pingPath).exchange()).hasStatus(404);
        }

        @Test
        void moreThanFivePingsPerSecondGet429WithRetryAfter() {
            for (int i = 0; i < 5; i++) {
                assertThat(mvc.get().uri(pingPath).exchange()).hasStatusOk();
            }

            var limited = mvc.get().uri(pingPath).exchange();

            assertThat(limited).hasStatus(429);
            assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
            assertThat(pingCount()).isEqualTo(5);

            clock.advance(Duration.ofSeconds(1));
            assertThat(mvc.get().uri(pingPath).exchange()).hasStatusOk();
        }
    }

    @Test
    void concurrentPingsForOneCheckAreAllRecorded() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Callable<MvcTestResult>>();
            for (int i = 0; i < 5; i++) {
                tasks.add(() -> mvc.get().uri(pingPath).exchange());
            }
            for (var future : executor.invokeAll(tasks)) {
                assertThat(future.get()).hasStatusOk();
            }
        }

        assertThat(pingCount()).isEqualTo(5);
    }

    record PingRow(String type, Integer exitCode, Instant receivedAt, Long durationMs, String sourceIp,
            String userAgent, String method, String body, boolean bodyTruncated) {
    }

    private java.util.List<PingRow> pings() {
        return jdbc.sql("""
                SELECT type, exit_code, received_at, duration_ms, host(source_ip) AS source_ip,
                       user_agent, method, body, body_truncated
                FROM pings ORDER BY id
                """).query(PingRow.class).list();
    }

    private PingRow onlyPing() {
        var pings = pings();
        assertThat(pings).hasSize(1);
        return pings.getFirst();
    }

    private long pingCount() {
        return jdbc.sql("SELECT count(*) FROM pings").query(Long.class).single();
    }

    private JsonNode check() {
        return body(mvc.get().uri("/api/v1/checks/{id}", checkId).exchange());
    }

    private JsonNode body(MvcTestResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
