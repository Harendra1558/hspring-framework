package com.project.MultiThreadedWebServer.exception;

import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GlobalExceptionHandler handles all exceptions that occur during request processing.
 * Similar to Spring's @ControllerAdvice and @ExceptionHandler.
 * 
 * This provides a centralized way to:
 * 1. Log exceptions consistently
 * 2. Return appropriate error responses
 * 3. Hide internal error details from clients
 * 
 * Example custom exception handlers can be added for specific exception types.
 */
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles any exception and sends an appropriate error response.
     * 
     * @param request   The HTTP request that caused the exception
     * @param response  The HTTP response to send the error
     * @param exception The exception that occurred
     */
    public void handleException(HttpRequest request, HttpResponse response, Exception exception) {
        logger.error("Exception during request [{}] {}: {}", 
                request.getMethod(), 
                request.getPath(), 
                exception.getMessage(), 
                exception);

        // Check for specific exception types
        if (exception instanceof IllegalArgumentException) {
            handleBadRequest(response, exception);
        } else if (exception instanceof NotFoundException) {
            handleNotFound(response, exception);
        } else if (exception instanceof ValidationException) {
            handleValidationError(response, (ValidationException) exception);
        } else if (exception instanceof UnauthorizedException) {
            handleUnauthorized(response, exception);
        } else {
            handleInternalError(response, exception);
        }
    }

    private void handleBadRequest(HttpResponse response, Exception exception) {
        String json = String.format(
                "{\"error\": \"Bad Request\", \"message\": \"%s\", \"status\": 400}",
                escapeJson(exception.getMessage())
        );
        response.status(HttpResponse.BAD_REQUEST).json(json);
    }

    private void handleNotFound(HttpResponse response, Exception exception) {
        String json = String.format(
                "{\"error\": \"Not Found\", \"message\": \"%s\", \"status\": 404}",
                escapeJson(exception.getMessage())
        );
        response.status(HttpResponse.NOT_FOUND).json(json);
    }

    private void handleValidationError(HttpResponse response, ValidationException exception) {
        String json = String.format(
                "{\"error\": \"Validation Error\", \"message\": \"%s\", \"field\": \"%s\", \"status\": 400}",
                escapeJson(exception.getMessage()),
                exception.getField()
        );
        response.status(HttpResponse.BAD_REQUEST).json(json);
    }

    private void handleUnauthorized(HttpResponse response, Exception exception) {
        String json = String.format(
                "{\"error\": \"Unauthorized\", \"message\": \"%s\", \"status\": 401}",
                escapeJson(exception.getMessage())
        );
        response.status(HttpResponse.UNAUTHORIZED).json(json);
    }

    private void handleInternalError(HttpResponse response, Exception exception) {
        // Don't expose internal error details to clients
        String json = "{\"error\": \"Internal Server Error\", \"message\": \"An unexpected error occurred\", \"status\": 500}";
        response.status(HttpResponse.INTERNAL_SERVER_ERROR).json(json);
    }

    /**
     * Escapes special characters for JSON strings.
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
