package com.project.MultiThreadedWebServer.exception;

/**
 * Exception thrown when the path exists but not for the request's HTTP method.
 * Results in HTTP 405 Method Not Allowed (with an "Allow" header).
 *
 * Similar to Spring's HttpRequestMethodNotSupportedException.
 */
public class MethodNotAllowedException extends RuntimeException {

    public MethodNotAllowedException(String message) {
        super(message);
    }
}
