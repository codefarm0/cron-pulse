package in.codefarm.cronpulse.api;

import in.codefarm.cronpulse.api.idempotency.IdempotencyKeyReusedException;
import in.codefarm.cronpulse.check.CheckNotFoundException;
import in.codefarm.cronpulse.check.InvalidScheduleException;
import in.codefarm.cronpulse.check.SlugTakenException;
import in.codefarm.cronpulse.check.StaleCheckException;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error from a REST controller into an RFC 9457 {@link ProblemDetail}
 * ({@code application/problem+json}) with a stable {@code code} property.
 * <p>
 * Extending {@link ResponseEntityExceptionHandler} covers Spring MVC's own errors (405, 415, bad JSON…).
 * <p>
 * Deliberately not scoped to {@code @RestController}s: errors like 405 happen before a controller is
 * chosen, so a scoped advice would never see them. The Thymeleaf UI (Phase 4) adds its own
 * higher-priority advice for its controllers.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    record FieldError(String field, String message) {
    }

    @ExceptionHandler
    ProblemDetail handle(CheckNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, ErrorCode.CHECK_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(SlugTakenException e) {
        return problem(HttpStatus.CONFLICT, ErrorCode.SLUG_TAKEN, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(StaleCheckException e) {
        return problem(HttpStatus.PRECONDITION_FAILED, ErrorCode.PRECONDITION_FAILED, e.getMessage());
    }

    /** Two requests updated the same check at the same moment; the database detected it via @Version. */
    @ExceptionHandler
    ProblemDetail handle(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified by another request at the same time. Fetch it again and retry.");
    }

    @ExceptionHandler
    ProblemDetail handle(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.IDEMPOTENCY_KEY_REUSED, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(InvalidScheduleException e) {
        var problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "The request has invalid fields.");
        problem.setProperty("errors", List.of(new FieldError(e.getField(), e.getMessage())));
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "The request has invalid fields.");
        problem.setProperty("errors", e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "The request has invalid parameters.");
        problem.setProperty("errors", e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(fieldName(result.getMethodParameter().getParameterName(), error),
                                error.getDefaultMessage())))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Don't echo parser internals back to the client; they can include class names.
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "The request body is missing or is not valid JSON for this endpoint."));
    }

    /** Gives Spring MVC's own errors (405, 415, 404 for unknown paths…) a code too, so every error has one. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && !hasCode(problem)) {
            var status = HttpStatus.resolve(statusCode.value());
            problem.setProperty("code", status != null ? status.name() : "ERROR");
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /**
     * When a handler has constraints on plain parameters (e.g. a header) as well as a {@code @Valid} body,
     * Spring validates the whole method and reports body errors here too. Use the body's field path
     * ("schedule.cron") rather than the parameter name ("request") so clients see the same field names
     * either way.
     */
    private static String fieldName(String parameterName, MessageSourceResolvable error) {
        return error instanceof org.springframework.validation.FieldError fieldError ? fieldError.getField() : parameterName;
    }

    private static boolean hasCode(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        return properties != null && properties.containsKey("code");
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }
}
