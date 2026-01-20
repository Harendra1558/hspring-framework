package com.project.MultiThreadedWebServer.exception;

/**
 * Custom exception for authentication/authorization errors (401).
 * Throw this when a user is not authenticated or authorized.
 * 
 * Example:
 * <pre>
 * String token = request.getHeader("Authorization");
 * if (token == null || !isValidToken(token)) {
 *     throw new UnauthorizedException("Invalid or missing authentication token");
 * }
 * </pre>
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
