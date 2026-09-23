package com.officeshuttle.booking.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

/**
 * Standardised RFC 7807 Global Exception Handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetailResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Domain exception [{}] [traceId: {}]: {}", ex.getClass().getSimpleName(), traceId, ex.getMessage());

        ProblemDetailResponse response = new ProblemDetailResponse(
                ex.getErrorType(),
                splitCamelCase(ex.getClass().getSimpleName().replace("Exception", "")),
                ex.getStatus(),
                ex.getMessage(),
                traceId,
                request.getRequestURI()
        );

        if (ex instanceof SegmentUnavailableException sue && sue.getWaitlistOfferAction() != null) {
            response.setWaitlistOffer(Map.of("action", sue.getWaitlistOfferAction()));
        }

        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetailResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        ProblemDetailResponse response = new ProblemDetailResponse(
                "https://api.shuttle/errors/forbidden",
                "Forbidden",
                HttpStatus.FORBIDDEN.value(),
                "Access denied: Insufficient privileges for this operation.",
                traceId,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetailResponse> handleAuthException(AuthenticationException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        ProblemDetailResponse response = new ProblemDetailResponse(
                "https://api.shuttle/errors/unauthorized",
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                traceId,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled server exception [traceId: {}]", traceId, ex);

        ProblemDetailResponse response = new ProblemDetailResponse(
                "https://api.shuttle/errors/internal-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred. Please contact support quoting traceId.",
                traceId,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String splitCamelCase(String s) {
        return s.replaceAll("(?<=[A-Z])(?=[A-Z][a-z])|(?<=[^A-Z])(?=[A-Z])|(?<=[A-Za-z])(?=[^A-Za-z])", " ");
    }
}
