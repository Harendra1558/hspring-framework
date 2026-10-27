package com.project.MultiThreadedWebServer.filter;

import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LoggingFilter logs information about each request and response.
 * This is a demonstration of how filters work.
 *
 * Similar to Spring's logging interceptors.
 *
 * WHY THE TIMING LIVES IN afterCompletion():
 * postHandle() is SKIPPED when the controller throws an exception, but
 * afterCompletion() ALWAYS runs (it's called from a finally block).
 * The ThreadLocal must be cleared on every request: worker threads are
 * pooled and reused, so a value left behind would leak into the next
 * request handled by the same thread.
 */
public class LoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    // Thread-local to track request start time
    // (one filter instance is shared by all worker threads, so a plain field would race)
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpRequest request, HttpResponse response) {
        startTime.set(System.currentTimeMillis());
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        logger.info("➡ Incoming Request");
        logger.info("   Method: {}", request.getMethod());
        logger.info("   Path: {}", request.getPath());
        if (!request.getQueryParams().isEmpty()) {
            logger.info("   Query Params: {}", request.getQueryParams());
        }
        logger.info("   Content-Type: {}", request.getContentType());
        return true; // Continue processing
    }

    @Override
    public void afterCompletion(HttpRequest request, HttpResponse response, Exception exception) {
        try {
            Long start = startTime.get();
            long duration = start == null ? -1 : System.currentTimeMillis() - start;
            if (exception != null) {
                logger.warn("⚠ Request completed with error: {}", exception.getMessage());
            }
            logger.info("⬅ Response Sent");
            logger.info("   Status: {}", response.getStatusCode());
            logger.info("   Duration: {} ms", duration);
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } finally {
            startTime.remove();
        }
    }

    @Override
    public int getOrder() {
        return 0; // Highest priority - runs first
    }
}
