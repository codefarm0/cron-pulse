package in.codefarm.cronpulse.check.api;

import in.codefarm.cronpulse.api.PageResponse;
import in.codefarm.cronpulse.api.idempotency.IdempotencyService;
import in.codefarm.cronpulse.check.Check;
import in.codefarm.cronpulse.check.CheckFilter;
import in.codefarm.cronpulse.check.CheckService;
import in.codefarm.cronpulse.check.CheckStatus;
import in.codefarm.cronpulse.config.CronPulseProperties;
import in.codefarm.cronpulse.tenant.TenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Management API for checks. Thin by design: it maps HTTP to {@link CheckService} calls and
 * entities to response records. Business rules live in the service.
 */
@RestController
@RequestMapping("/api/v1/checks")
@Tag(name = "Checks", description = "Create and manage monitored jobs")
class CheckApiController {

    private final CheckService checks;
    private final TenantResolver tenants;
    private final IdempotencyService idempotency;
    private final URI publicBaseUrl;

    CheckApiController(CheckService checks, TenantResolver tenants, IdempotencyService idempotency,
            CronPulseProperties properties) {
        this.checks = checks;
        this.tenants = tenants;
        this.idempotency = idempotency;
        this.publicBaseUrl = properties.publicBaseUrl();
    }

    @GetMapping
    @Operation(summary = "List checks", description = "Sorted by name. Filters are optional and combine with AND.")
    PageResponse<CheckResponse> list(
            @RequestParam(required = false) CheckStatus status,
            @RequestParam(required = false) @Size(max = 30) String tag,
            @Parameter(description = "Case-insensitive search in the name")
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Sorted by name in the query itself; see CheckRepository#search.
        var pageable = PageRequest.of(page, size);
        var result = checks.list(tenants.currentOrganizationId(), new CheckFilter(status, tag, q), pageable);
        return PageResponse.of(result, this::toResponse);
    }

    @PostMapping
    @Operation(summary = "Create a check",
            description = "Send an Idempotency-Key header to make retries safe: a repeated key returns the original response.")
    ResponseEntity<?> create(
            @RequestHeader(name = IdempotencyService.HEADER, required = false) @Size(min = 1, max = 255) String idempotencyKey,
            @Valid @RequestBody CheckRequest request) {
        long organizationId = tenants.currentOrganizationId();
        if (idempotencyKey == null) {
            return created(checks.create(organizationId, request.toDetails()));
        }
        return idempotency.execute(organizationId, idempotencyKey, request,
                () -> created(checks.create(organizationId, request.toDetails())));
    }

    @GetMapping("/summary")
    @Operation(summary = "Count checks by status")
    StatusSummaryResponse summary() {
        var counts = checks.countByStatus(tenants.currentOrganizationId());
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new StatusSummaryResponse(total, counts);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a check", description = "The ETag header holds the version; send it back in If-Match when updating.")
    ResponseEntity<CheckResponse> get(@PathVariable UUID id) {
        return withETag(checks.get(tenants.currentOrganizationId(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a check",
            description = "Replaces all editable fields. With If-Match, the update fails with 412 if the check changed since you read it.")
    ResponseEntity<CheckResponse> update(@PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody CheckRequest request) {
        var check = checks.update(tenants.currentOrganizationId(), id, request.toDetails(), ETags.parseVersion(ifMatch));
        return withETag(check);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a check and its ping history")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        checks.delete(tenants.currentOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause monitoring", description = "No alerts while paused. Pausing twice is harmless.")
    ResponseEntity<CheckResponse> pause(@PathVariable UUID id) {
        return withETag(checks.pause(tenants.currentOrganizationId(), id));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume monitoring", description = "The check becomes NEW and turns UP on its next ping.")
    ResponseEntity<CheckResponse> resume(@PathVariable UUID id) {
        return withETag(checks.resume(tenants.currentOrganizationId(), id));
    }

    private ResponseEntity<?> created(Check check) {
        var location = URI.create("/api/v1/checks/" + check.getPublicId());
        return ResponseEntity.created(location).eTag(ETags.of(check)).body(toResponse(check));
    }

    private ResponseEntity<CheckResponse> withETag(Check check) {
        return ResponseEntity.ok().eTag(ETags.of(check)).body(toResponse(check));
    }

    private CheckResponse toResponse(Check check) {
        return CheckResponse.from(check, publicBaseUrl);
    }
}
