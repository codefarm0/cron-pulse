package in.codefarm.cronpulse.api.idempotency;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes a POST safe to retry: the first request with a given {@code Idempotency-Key} runs and its
 * response is stored; later requests with the same key get the stored response replayed.
 * <p>
 * Why it works under concurrency: the key row is inserted in the <em>same transaction</em> as the
 * work. If two requests with the same key arrive together, the second {@code INSERT … ON CONFLICT}
 * waits on the first transaction's uncommitted row. If the first commits, the second finds the stored
 * response and replays it; if the first fails and rolls back, the second simply does the work itself.
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    static final Duration RETENTION = Duration.ofHours(24);

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    IdempotencyService(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Runs {@code action} once per (organisation, key) and replays its response afterwards.
     *
     * @param request the parsed request body; its hash detects a key reused for a different request
     */
    @Transactional
    public ResponseEntity<?> execute(long organizationId, String key, Object request, Supplier<ResponseEntity<?>> action) {
        var now = clock.instant();
        var requestHash = sha256(json.writeValueAsBytes(request));

        // An expired key behaves as if it was never used.
        jdbc.sql("DELETE FROM idempotency_keys WHERE organization_id = ? AND idempotency_key = ? AND created_at < ?")
                .params(organizationId, key, now.minus(RETENTION).atOffset(ZoneOffset.UTC))
                .update();

        int inserted = jdbc.sql("""
                        INSERT INTO idempotency_keys (organization_id, idempotency_key, request_hash, created_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """)
                .params(organizationId, key, requestHash, now.atOffset(ZoneOffset.UTC))
                .update();

        if (inserted == 0) {
            return replay(organizationId, key, requestHash);
        }

        var response = action.get();
        var location = response.getHeaders().getLocation();
        jdbc.sql("""
                        UPDATE idempotency_keys SET response_status = ?, response_body = ?, location = ?
                        WHERE organization_id = ? AND idempotency_key = ?
                        """)
                .params(response.getStatusCode().value(), json.writeValueAsString(response.getBody()),
                        location != null ? location.toString() : null, organizationId, key)
                .update();
        return response;
    }

    private ResponseEntity<?> replay(long organizationId, String key, String requestHash) {
        var stored = jdbc.sql("""
                        SELECT request_hash, response_status, response_body, location
                        FROM idempotency_keys WHERE organization_id = ? AND idempotency_key = ?
                        """)
                .params(organizationId, key)
                .query((rs, row) -> new StoredResponse(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4)))
                .single();

        if (!stored.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        var builder = ResponseEntity.status(HttpStatus.valueOf(stored.status()))
                .contentType(MediaType.APPLICATION_JSON)
                .header(REPLAYED_HEADER, "true");
        if (stored.location() != null) {
            builder.header(HttpHeaders.LOCATION, URI.create(stored.location()).toString());
        }
        return builder.body(stored.body());
    }

    /** Deletes expired keys. Plain DELETE by age is safe to run on every instance at once. */
    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void deleteExpiredKeys() {
        jdbc.sql("DELETE FROM idempotency_keys WHERE created_at < ?")
                .param(clock.instant().minus(RETENTION).atOffset(ZoneOffset.UTC))
                .update();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }

    private record StoredResponse(String requestHash, int status, String body, String location) {
    }
}
