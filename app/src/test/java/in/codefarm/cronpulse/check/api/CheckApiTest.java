package in.codefarm.cronpulse.check.api;

import static org.assertj.core.api.Assertions.assertThat;

import in.codefarm.cronpulse.TestcontainersConfiguration;
import in.codefarm.cronpulse.check.Check;
import in.codefarm.cronpulse.check.CheckRepository;
import in.codefarm.cronpulse.check.Schedule;
import in.codefarm.cronpulse.tenant.Organization;
import in.codefarm.cronpulse.tenant.OrganizationRepository;
import in.codefarm.cronpulse.tenant.Plan;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end tests of /api/v1/checks: real Spring context, real Postgres, HTTP through MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckApiTest {

    static final String HOURLY = """
            {"name": "Nightly DB backup", "tags": ["prod"],
             "schedule": {"type": "SIMPLE", "periodSeconds": 3600}, "graceSeconds": 300}
            """;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("DELETE FROM idempotency_keys").update();
        jdbc.sql("DELETE FROM checks").update();
    }

    @Nested
    class Create {

        @Test
        void returnsCreatedWithLocationETagAndPingUrl() {
            var result = post(HOURLY);

            assertThat(result).hasStatus(201);
            var body = body(result);
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/checks/" + body.get("id").asString());
            assertThat(result.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"0\"");
            assertThat(body.get("slug").asString()).isEqualTo("nightly-db-backup");
            assertThat(body.get("status").asString()).isEqualTo("NEW");
            assertThat(body.get("pingUrl").asString()).startsWith("http://localhost:8080/ping/");
            assertThat(body.get("schedule").get("timeZone").asString()).isEqualTo("UTC");
        }

        @Test
        void acceptsACronScheduleInATimeZone() {
            var result = post("""
                    {"name": "Backup", "schedule": {"type": "CRON", "cron": "0 2 * * *", "timeZone": "Asia/Kolkata"}, "graceSeconds": 900}
                    """);

            assertThat(result).hasStatus(201);
            assertThat(body(result).get("schedule").get("cron").asString()).isEqualTo("0 2 * * *");
        }

        @Test
        void rejectsInvalidFieldsWithAllErrorsListed() {
            var result = post("""
                    {"name": "", "slug": "Not A Slug", "schedule": {"type": "SIMPLE", "periodSeconds": 3600}}
                    """);

            assertProblem(result, 400, "VALIDATION_FAILED");
            assertThat(body(result).get("errors").findValuesAsString("field"))
                    .containsExactlyInAnyOrder("name", "slug", "graceSeconds");
        }

        @Test
        void rejectsAPeriodShorterThanOneMinute() {
            var result = post("""
                    {"name": "Fast", "schedule": {"type": "SIMPLE", "periodSeconds": 10}, "graceSeconds": 0}
                    """);

            assertProblem(result, 400, "VALIDATION_FAILED");
            assertThat(body(result).get("errors").get(0).get("field").asString()).isEqualTo("schedule.periodSeconds");
        }

        @Test
        void rejectsAnInvalidCronExpression() {
            var result = post("""
                    {"name": "Bad", "schedule": {"type": "CRON", "cron": "0 25 * * *"}, "graceSeconds": 0}
                    """);

            assertProblem(result, 400, "VALIDATION_FAILED");
            assertThat(body(result).get("errors").get(0).get("field").asString()).isEqualTo("schedule.cron");
        }

        @Test
        void rejectsAnUnknownTimeZone() {
            var result = post("""
                    {"name": "Bad", "schedule": {"type": "CRON", "cron": "0 2 * * *", "timeZone": "Mars/Olympus"}, "graceSeconds": 0}
                    """);

            assertProblem(result, 400, "VALIDATION_FAILED");
            assertThat(body(result).get("errors").get(0).get("field").asString()).isEqualTo("timeZone");
        }

        @Test
        void rejectsMalformedJson() {
            assertProblem(post("{\"name\": "), 400, "MALFORMED_REQUEST");
            assertProblem(post("""
                    {"name": "x", "schedule": {"type": "HOURLY"}, "graceSeconds": 0}
                    """), 400, "MALFORMED_REQUEST");
        }

        @Test
        void rejectsADuplicateSlug() {
            post(HOURLY);

            assertProblem(post(HOURLY), 409, "SLUG_TAKEN");
        }
    }

    @Nested
    class Idempotency {

        @Test
        void sameKeyAndBodyReplaysTheOriginalResponse() {
            var first = post(HOURLY, "key-1");
            var second = post(HOURLY, "key-1");

            assertThat(second).hasStatus(201);
            assertThat(second.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
            assertThat(body(second).get("id")).isEqualTo(body(first).get("id"));
            assertThat(second.getResponse().getHeader(HttpHeaders.LOCATION))
                    .isEqualTo(first.getResponse().getHeader(HttpHeaders.LOCATION));
            assertThat(checkCount()).isEqualTo(1);
        }

        @Test
        void sameKeyWithADifferentBodyIsRejected() {
            post(HOURLY, "key-1");

            assertProblem(post(HOURLY.replace("Nightly DB backup", "Other"), "key-1"), 422, "IDEMPOTENCY_KEY_REUSED");
            assertThat(checkCount()).isEqualTo(1);
        }

        @Test
        void concurrentRequestsWithTheSameKeyCreateExactlyOneCheck() throws Exception {
            var tasks = new ArrayList<Callable<MvcTestResult>>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> post(HOURLY, "race-key"));
            }
            try (var pool = Executors.newFixedThreadPool(8)) {
                for (var future : pool.invokeAll(tasks)) {
                    assertThat(future.get()).hasStatus(201);
                }
            }

            assertThat(checkCount()).isEqualTo(1);
        }

        @Test
        void failedRequestDoesNotBurnTheKey() {
            assertProblem(post("{\"name\": \"\"}", "key-2"), 400, "VALIDATION_FAILED");

            assertThat(post(HOURLY, "key-2")).hasStatus(201);
        }
    }

    @Nested
    class ReadAndList {

        @Test
        void getReturnsTheCheckWithItsETag() {
            var id = body(post(HOURLY)).get("id").asString();

            var result = mvc.get().uri("/api/v1/checks/{id}", id).exchange();

            assertThat(result).hasStatusOk();
            assertThat(result.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"0\"");
        }

        @Test
        void unknownIdIsNotFound() {
            assertProblem(mvc.get().uri("/api/v1/checks/{id}", "3f1c6a3e-0000-4000-8000-000000000000").exchange(),
                    404, "CHECK_NOT_FOUND");
        }

        @Test
        void anotherTenantsCheckIsNotFound(@Autowired OrganizationRepository organizations,
                @Autowired CheckRepository checks) {
            var other = organizations.save(new Organization("Other", "other-" + System.nanoTime(), Plan.FREE, Instant.now()));
            var foreign = checks.save(new Check(other.getId(), "Secret", "secret", List.of(),
                    Schedule.every(Duration.ofHours(1)), Duration.ZERO, Instant.now()));

            assertProblem(mvc.get().uri("/api/v1/checks/{id}", foreign.getPublicId()).exchange(), 404, "CHECK_NOT_FOUND");
            assertProblem(mvc.delete().uri("/api/v1/checks/{id}", foreign.getPublicId()).exchange(), 404, "CHECK_NOT_FOUND");
            assertThat(body(mvc.get().uri("/api/v1/checks").exchange()).get("totalItems").asLong()).isZero();
        }

        @Test
        void listsWithPagingAndFilters() {
            post(HOURLY);
            post("""
                    {"name": "Invoice generator", "tags": ["billing"], "schedule": {"type": "SIMPLE", "periodSeconds": 3600}, "graceSeconds": 0}
                    """);
            post("""
                    {"name": "Search sync", "tags": ["prod"], "schedule": {"type": "SIMPLE", "periodSeconds": 900}, "graceSeconds": 0}
                    """);

            var firstPage = body(mvc.get().uri("/api/v1/checks?size=2").exchange());
            assertThat(firstPage.get("totalItems").asLong()).isEqualTo(3);
            assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
            assertThat(firstPage.get("items").findValuesAsString("name"))
                    .containsExactly("Invoice generator", "Nightly DB backup");

            assertThat(names("/api/v1/checks?tag=prod")).containsExactly("Nightly DB backup", "Search sync");
            assertThat(names("/api/v1/checks?q=SYNC")).containsExactly("Search sync");
            assertThat(names("/api/v1/checks?status=PAUSED")).isEmpty();
        }

        @Test
        void searchTreatsWildcardsLiterally() {
            post("""
                    {"name": "50% rollout", "schedule": {"type": "SIMPLE", "periodSeconds": 3600}, "graceSeconds": 0}
                    """);
            post("""
                    {"name": "500 jobs", "schedule": {"type": "SIMPLE", "periodSeconds": 3600}, "graceSeconds": 0}
                    """);

            var result = mvc.get().uri("/api/v1/checks").param("q", "50%").exchange();

            assertThat(body(result).get("items").findValuesAsString("name")).containsExactly("50% rollout");
        }

        @Test
        void rejectsAPageSizeAboveTheLimit() {
            assertProblem(mvc.get().uri("/api/v1/checks?size=500").exchange(), 400, "VALIDATION_FAILED");
        }

        @Test
        void summaryCountsEveryStatus() {
            post(HOURLY);

            var summary = body(mvc.get().uri("/api/v1/checks/summary").exchange());

            assertThat(summary.get("total").asLong()).isEqualTo(1);
            assertThat(summary.get("byStatus").get("NEW").asLong()).isEqualTo(1);
            assertThat(summary.get("byStatus").get("DOWN").asLong()).isZero();
        }
    }

    @Nested
    class UpdateAndLifecycle {

        @Test
        void updateWithCurrentETagSucceedsAndReturnsANewOne() {
            var id = body(post(HOURLY)).get("id").asString();

            var result = put(id, HOURLY.replace("Nightly DB backup", "Renamed"), "\"0\"");

            assertThat(result).hasStatusOk();
            assertThat(result.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"1\"");
            assertThat(body(result).get("name").asString()).isEqualTo("Renamed");
        }

        @Test
        void updateWithStaleETagIsRejected() {
            var id = body(post(HOURLY)).get("id").asString();
            put(id, HOURLY.replace("Nightly DB backup", "First edit"), "\"0\"");

            assertProblem(put(id, HOURLY.replace("Nightly DB backup", "Lost update"), "\"0\""), 412, "PRECONDITION_FAILED");
        }

        @Test
        void updateWithoutIfMatchIsAllowed() {
            var id = body(post(HOURLY)).get("id").asString();

            assertThat(put(id, HOURLY.replace("Nightly DB backup", "Renamed"), null)).hasStatusOk();
        }

        @Test
        void pauseAndResumeRecordStatusChanges() {
            var id = body(post(HOURLY)).get("id").asString();

            var paused = mvc.post().uri("/api/v1/checks/{id}/pause", id).exchange();
            assertThat(body(paused).get("status").asString()).isEqualTo("PAUSED");
            assertThat(body(paused).get("pausedAt").isNull()).isFalse();

            var resumed = mvc.post().uri("/api/v1/checks/{id}/resume", id).exchange();
            assertThat(body(resumed).get("status").asString()).isEqualTo("NEW");
            assertThat(body(resumed).get("pausedAt").isNull()).isTrue();

            var transitions = jdbc.sql("""
                    SELECT sc.from_status || '->' || sc.to_status FROM status_changes sc
                    JOIN checks c ON c.id = sc.check_id WHERE c.public_id = ?::uuid ORDER BY sc.id
                    """).param(id).query(String.class).list();
            assertThat(transitions).containsExactly("NEW->PAUSED", "PAUSED->NEW");
        }

        @Test
        void deleteRemovesTheCheck() {
            var id = body(post(HOURLY)).get("id").asString();

            assertThat(mvc.delete().uri("/api/v1/checks/{id}", id).exchange()).hasStatus(204);
            assertProblem(mvc.get().uri("/api/v1/checks/{id}", id).exchange(), 404, "CHECK_NOT_FOUND");
        }

        @Test
        void unsupportedMethodGetsAProblemWithACode() {
            var id = body(post(HOURLY)).get("id").asString();

            assertProblem(mvc.patch().uri("/api/v1/checks/{id}", id).exchange(), 405, "METHOD_NOT_ALLOWED");
        }
    }

    private MvcTestResult post(String body) {
        return post(body, null);
    }

    private MvcTestResult post(String body, String idempotencyKey) {
        var request = mvc.post().uri("/api/v1/checks").contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return request.exchange();
    }

    private MvcTestResult put(String id, String body, String ifMatch) {
        var request = mvc.put().uri("/api/v1/checks/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body);
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    private List<String> names(String uri) {
        return body(mvc.get().uri(uri).exchange()).get("items").findValuesAsString("name");
    }

    private JsonNode body(MvcTestResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertProblem(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body(result).get("code").asString()).isEqualTo(code);
    }

    private long checkCount() {
        return jdbc.sql("SELECT count(*) FROM checks").query(Long.class).single();
    }
}
