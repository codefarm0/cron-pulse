package in.codefarm.cronpulse.ping;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The URLs jobs call. Unauthenticated: the unguessable ping UUID is the credential.
 * <p>
 * Responses are tiny plain text, not JSON or problem+json: the client is usually {@code curl} in a
 * shell script, and nobody parses the body. The status code is what matters.
 * <p>
 * GET, POST and HEAD all work, because jobs use whatever their HTTP client makes easiest.
 */
@RestController
@RequestMapping(path = "/ping/{pingUuid}", method = {GET, POST, HEAD})
class PingController {

    private final PingService pingService;

    PingController(PingService pingService) {
        this.pingService = pingService;
    }

    @RequestMapping
    ResponseEntity<String> success(@PathVariable String pingUuid, HttpServletRequest request) throws IOException {
        return record(pingUuid, PingSignal.success(), request);
    }

    @RequestMapping("/start")
    ResponseEntity<String> start(@PathVariable String pingUuid, HttpServletRequest request) throws IOException {
        return record(pingUuid, PingSignal.start(), request);
    }

    @RequestMapping("/fail")
    ResponseEntity<String> fail(@PathVariable String pingUuid, HttpServletRequest request) throws IOException {
        return record(pingUuid, PingSignal.fail(), request);
    }

    /** {@code curl …/ping/<uuid>/$?} reports the job's exit code: 0 is success, 1–255 is failure. */
    @RequestMapping("/{exitCode:\\d+}")
    ResponseEntity<String> exitCode(@PathVariable String pingUuid, @PathVariable String exitCode,
            HttpServletRequest request) throws IOException {
        // Compare as text first: "99999999999" would overflow an int.
        if (exitCode.length() > 3 || Integer.parseInt(exitCode) > PingSignal.MAX_EXIT_CODE) {
            return text(HttpStatus.BAD_REQUEST, "Exit code must be between 0 and 255");
        }
        return record(pingUuid, PingSignal.exitCode(Integer.parseInt(exitCode)), request);
    }

    private ResponseEntity<String> record(String pingUuid, PingSignal signal, HttpServletRequest request)
            throws IOException {
        var uuid = parseUuid(pingUuid);
        if (uuid == null) {
            return notFound();
        }
        var source = PingSource.of(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT),
                request.getMethod());
        return switch (pingService.record(uuid, signal, source, request.getInputStream())) {
            case PingOutcome.Recorded recorded -> text(HttpStatus.OK, "OK");
            case PingOutcome.UnknownCheck unknown -> notFound();
            case PingOutcome.RateLimited limited -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    // Whole seconds, rounded up, never 0: "retry after 0 seconds" invites a busy loop.
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ceilSeconds(limited))))
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Rate limit exceeded");
        };
    }

    private static long ceilSeconds(PingOutcome.RateLimited limited) {
        var retryAfter = limited.retryAfter();
        return retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
    }

    /** A malformed UUID can't belong to any check, so it gets the same 404 as an unknown one. */
    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ResponseEntity<String> notFound() {
        return text(HttpStatus.NOT_FOUND, "Not found");
    }

    private static ResponseEntity<String> text(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(body);
    }
}
