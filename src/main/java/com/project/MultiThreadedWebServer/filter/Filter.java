package com.project.MultiThreadedWebServer.filter;

import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;

/**
 * Filter interface for implementing request/response interceptors.
 * Similar to Spring's Filter or HandlerInterceptor.
 * 
 * Filters allow you to:
 * - Execute logic BEFORE a request reaches the controller
 * - Execute logic AFTER the controller processes the request
 * - Modify the request/response
 * - Short-circuit the request (e.g., for authentication)
 * 
 * Example:
 * <pre>
 * public class LoggingFilter implements Filter {
 *     @Override
 *     public boolean preHandle(HttpRequest request, HttpResponse response) {
 *         System.out.println("Incoming request: " + request.getPath());
 *         return true; // Continue processing
 *     }
 *     
 *     @Override
 *     public void postHandle(HttpRequest request, HttpResponse response) {
 *         System.out.println("Request completed: " + request.getPath());
 *     }
 * }
 * </pre>
 */
public interface Filter {

    /**
     * Called BEFORE the request is handled by the controller.
     * 
     * @param request  The incoming HTTP request
     * @param response The HTTP response object
     * @return true to continue processing, false to stop (short-circuit)
     */
    boolean preHandle(HttpRequest request, HttpResponse response);

    /**
     * Called AFTER the controller has processed the request.
     * This is only called if preHandle returned true.
     * 
     * @param request  The HTTP request
     * @param response The HTTP response
     */
    default void postHandle(HttpRequest request, HttpResponse response) {
        // Default: do nothing
    }

    /**
     * Called after the complete request has finished.
     * Always called, even if an exception occurred.
     * Useful for cleanup operations.
     * 
     * @param request   The HTTP request
     * @param response  The HTTP response
     * @param exception Any exception that occurred during processing, or null
     */
    default void afterCompletion(HttpRequest request, HttpResponse response, Exception exception) {
        // Default: do nothing
    }

    /**
     * Returns the order of this filter. Lower values = higher priority.
     * Filters are executed in order from lowest to highest.
     * 
     * @return The filter order (default is 0)
     */
    default int getOrder() {
        return 0;
    }
}
