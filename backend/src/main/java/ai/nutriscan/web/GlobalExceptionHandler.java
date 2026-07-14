package ai.nutriscan.web;

import ai.nutriscan.application.service.NotFoundException;
import ai.nutriscan.application.service.QuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/** Respuestas de error uniformes; nunca se filtran stacktraces al cliente. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    record ApiError(int status, String error, String message, Instant timestamp) {
        static ApiError of(HttpStatus status, String message) {
            return new ApiError(status.value(), status.getReasonPhrase(), message, Instant.now());
        }
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiError> quota(QuotaExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiError.of(HttpStatus.PAYMENT_REQUIRED, e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> badCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        var errors = new java.util.HashMap<String, String>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> errors.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor"));
    }
}
