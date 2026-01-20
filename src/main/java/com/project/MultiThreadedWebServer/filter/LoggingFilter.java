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
 */
public class LoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    // Thread-local to track request start time
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
    public void postHandle(HttpRequest request, HttpResponse response) {
        long duration = System.currentTimeMillis() - startTime.get();
        logger.info("⬅ Response Sent");
        logger.info("   Status: {}", response.getStatusCode());
        logger.info("   Duration: {} ms", duration);
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        startTime.remove();
    }

    @Override
    public void afterCompletion(HttpRequest request, HttpResponse response, Exception exception) {
        if (exception != null) {
            logger.error("⚠ Request completed with error: {}", exception.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return 0; // Highest priority - runs first
    }
}
