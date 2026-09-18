package com.rageval.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a trace id to every incoming HTTP request and puts it in the SLF4J MDC,
 * so every log line emitted while handling that request (controller, service,
 * repository, WebSocket broadcast triggered synchronously from the same thread)
 * carries the same "traceId" field. Lets a single query's full execution path be
 * grepped out of the logs by one id, and be correlated with the client via the
 * X-Trace-Id response header.
 *
 * Runs at the highest precedence so the trace id is present before any other
 * filter or controller code logs anything for this request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // Reuse an inbound trace id if the caller already supplied one (useful for
        // stitching logs across services later); otherwise mint a new one.
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // MUST clear: servlet containers reuse threads across requests (thread pool),
            // so a stale MDC value would otherwise leak into the next, unrelated request's logs.
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
