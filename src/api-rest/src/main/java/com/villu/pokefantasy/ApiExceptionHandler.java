package com.villu.pokefantasy;

import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todos los errores salen como {@link ProblemDetail} (RFC 9457, {@code application/problem+json}):
 * {@code status}, {@code title}, {@code detail} y dos propiedades propias:
 * <ul>
 *   <li>{@code code}: identificador estable para que el cliente distinga casos sin parsear textos
 *       (p. ej. {@code CONCURRENT_MODIFICATION}, {@code TOO_MANY_ATTEMPTS}).</li>
 *   <li>{@code message}: igual que {@code detail}; es lo que lee el frontend
 *       ({@code extractErrorMessage} usa {@code data.message}).</li>
 *   <li>{@code requestId}: el de {@link RequestIdFilter}, para buscar el error en los logs.</li>
 * </ul>
 * Los errores propios de Spring MVC (método no soportado, JSON mal formado, parámetro ausente, ruta
 * inexistente…) los resuelve {@link ResponseEntityExceptionHandler} con su código HTTP correcto
 * (405, 400, 404…) en vez de acabar como 500.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return withCode(problem, code);
    }

    private static ProblemDetail withCode(ProblemDetail problem, String code) {
        problem.setProperty("code", code);
        problem.setProperty("message", problem.getDetail());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            // Para que el usuario pueda dar un id con el que buscar el error en los logs.
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(problem(status, code, detail));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials() {
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyAttemptsException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfter().toSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", exception.getMessage()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(ForbiddenOperationException exception) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException exception) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleConflict(IllegalStateException exception) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock() {
        return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "Otro jugador modificó los datos al mismo tiempo. Inténtalo de nuevo.");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateKey(DuplicateKeyException exception) {
        return respond(HttpStatus.CONFLICT, "DUPLICATE", exception.getMessage());
    }

    @ExceptionHandler(ClientAbortException.class)
    public ResponseEntity<Void> handleClientAbort(Exception exception) {
        log.debug("Client disconnected: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    /** Errores de Spring MVC: se mantiene su ProblemDetail y se le añaden {@code code} y {@code message}. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            withCode(problem, status != null ? status.name() : "HTTP_" + statusCode.value());
        }
        return response;
    }
}
