package com.officeshuttle.booking.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures Idempotency-Key header is present on mutating booking endpoints.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Enforce on POST /api/v1/trips/{id}/bookings
        if ("POST".equalsIgnoreCase(method) && path.matches(".*/api/v1/trips/\\d+/bookings.*")) {
            String idempotencyKey = request.getHeader("Idempotency-Key");
            if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("""
                    {
                        "type": "https://api.shuttle/errors/missing-idempotency-key",
                        "title": "Missing Idempotency Key",
                        "status": 400,
                        "detail": "The 'Idempotency-Key' header is mandatory on booking mutations.",
                        "instance": "%s"
                    }
                """.formatted(path));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
