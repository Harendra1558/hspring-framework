package com.project.MultiThreadedWebServer.exception;

import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.project.MultiThreadedWebServer.core.HttpResponse.escapeJson;

/**
 * GlobalExceptionHandler handles all exceptions that occur during request processing.
 * Similar to Spring's @ControllerAdvice and @ExceptionHandler.
 *
 * This provides a centralized way to:
 * 1. Log exceptions consistently
 * 2. Return appropriate error responses
 * 3. Hide internal error details from clients
 *
 * EXCEPTION → STATUS MAPPING:
 * ┌──────────────────────────────┬────────────────────────────────────────────┐
 * │  ValidationException         │  400 Bad Request  (+ "field")              │
 * │  IllegalArgumentException    │  400 Bad Request  (e.g. malformed HTTP)    │
 * │  UnauthorizedException       │  401 Unauthorized                          │
 * │  NotFoundException           │  404 Not Found                             │
 * │  MethodNotAllowedException   │  405 Method Not Allowed                    │
 * │  anything else               │  500 Internal Server Error (details hidden)│
 * └──────────────────────────────┴────────────────────────────────────────────┘
 *
 * Error body (similar to Spring Boot's default error attributes):
 *   {"error": "Not Found", "message": "User not found with id: 9", "status": 404, "path": "/api/users/9"}
 *
 * Example custom exception handlers can be added for specific exception types.
 */
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles any exception and sends an appropriate error response.
     *
     * @param request   The HTTP request that caused the exception (null if it couldn't be parsed)
     * @param response  The HTTP response to send the error
     * @param exception The exception that occurred
     */
    public void handleException(HttpRequest request, HttpResponse response, Exception exception) {
        String method = request != null ? request.getMethod() : "?";
        String path = request != null ? request.getPath() : "?";

        // Check for specific exception types
        // NOTE: order matters for subclasses - check the most specific type first
        if (exception instanceof ValidationException validation) {
            logClientError(method, path, exception);
            send(response, HttpResponse.BAD_REQUEST, "Validation Error", exception.getMessage(), path,
                    validation.getField());
        } else if (exception instanceof IllegalArgumentException) {
            logClientError(method, path, exception);
            send(response, HttpResponse.BAD_REQUEST, "Bad Request", exception.getMessage(), path, null);
        } else if (exception instanceof NotFoundException) {
            logClientError(method, path, exception);
            send(response, HttpResponse.NOT_FOUND, "Not Found", exception.getMessage(), path, null);
        } else if (exception instanceof UnauthorizedException) {
            logClientError(method, path, exception);
            send(response, HttpResponse.UNAUTHORIZED, "Unauthorized", exception.getMessage(), path, null);
        } else if (exception instanceof MethodNotAllowedException) {
            logClientError(method, path, exception);
            send(response, HttpResponse.METHOD_NOT_ALLOWED, "Method Not Allowed", exception.getMessage(), path, null);
        } else {
            // A real bug: log the full stack trace, but don't expose internal error details to clients
            logger.error("Unhandled exception during request [{}] {}", method, path, exception);
            send(response, HttpResponse.INTERNAL_SERVER_ERROR, "Internal Server Error",
                    "An unexpected error occurred", path, null);
        }
    }

    /**
     * 4xx errors are the CLIENT's fault and expected in normal operation:
     * log one line, without a stack trace, so real errors stay visible.
     */
    private void logClientError(String method, String path, Exception exception) {
        logger.warn("[{}] {} → {}: {}", method, path, exception.getClass().getSimpleName(), exception.getMessage());
    }

    private void send(HttpResponse response, int status, String error, String message, String path, String field) {
        StringBuilder json = new StringBuilder()
                .append("{\"error\": \"").append(escapeJson(error))
                .append("\", \"message\": \"").append(escapeJson(message)).append('"');
        if (field != null) {
            json.append(", \"field\": \"").append(escapeJson(field)).append('"');
        }
        json.append(", \"status\": ").append(status)
            .append(", \"path\": \"").append(escapeJson(path)).append("\"}");
        response.status(status).json(json.toString());
    }
}
