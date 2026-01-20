package com.project.MultiThreadedWebServer.exception;

/**
 * Custom exception for validation errors.
 * Throw this when input data fails validation.
 * 
 * Example:
 * <pre>
 * if (user.getEmail() == null || !user.getEmail().contains("@")) {
 *     throw new ValidationException("email", "A valid email address is required");
 * }
 * </pre>
 */
public class ValidationException extends RuntimeException {

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
