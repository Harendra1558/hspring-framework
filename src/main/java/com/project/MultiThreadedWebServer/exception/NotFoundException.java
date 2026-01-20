package com.project.MultiThreadedWebServer.exception;

/**
 * Custom exception for "Not Found" (404) errors.
 * Throw this from controllers when a requested resource doesn't exist.
 * 
 * Example:
 * <pre>
 * User user = userService.findById(id);
 * if (user == null) {
 *     throw new NotFoundException("User not found with id: " + id);
 * }
 * </pre>
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
