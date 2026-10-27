package com.project.MultiThreadedWebServer.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestTest {

    private static HttpRequest parse(String raw) throws IOException {
        return HttpRequest.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesRequestLinePathAndQueryParams() throws IOException {
        HttpRequest request = parse("GET /api/users?limit=5&name=John%20Doe&flag HTTP/1.1\r\nHost: localhost\r\n\r\n");

        assertEquals("GET", request.getMethod());
        assertEquals("/api/users", request.getPath());
        assertEquals("limit=5&name=John%20Doe&flag", request.getQueryString());
        assertEquals("5", request.getQueryParam("limit"));
        assertEquals("John Doe", request.getQueryParam("name"));
        assertEquals("", request.getQueryParam("flag"));
        assertEquals("10", request.getQueryParam("missing", "10"));
    }

    @Test
    void decodesMultiByteUtf8InQueryParams() throws IOException {
        HttpRequest request = parse("GET /search?q=caf%C3%A9+au+lait HTTP/1.1\r\n\r\n");
        assertEquals("café au lait", request.getQueryParam("q"));
    }

    @Test
    void headerNamesAreCaseInsensitive() throws IOException {
        HttpRequest request = parse("GET / HTTP/1.1\r\ncontent-type: application/json\r\nX-Token:abc\r\n\r\n");

        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("application/json", request.getContentType());
        assertEquals("abc", request.getHeader("x-token"), "space after the colon is optional");
    }

    @Test
    void readsBodyByByteCountNotCharCount() throws IOException {
        String body = "{\"name\":\"Zoë 日本\"}";
        int byteLength = body.getBytes(StandardCharsets.UTF_8).length;
        assertNotEquals(body.length(), byteLength, "precondition: body contains multi-byte chars");

        HttpRequest request = parse("POST /api/users HTTP/1.1\r\nContent-Length: " + byteLength + "\r\n\r\n" + body);

        assertEquals(body, request.getBody());
    }

    @Test
    void acceptsBareLfLineEndings() throws IOException {
        HttpRequest request = parse("GET /health HTTP/1.1\nHost: x\n\n");
        assertEquals("/health", request.getPath());
        assertEquals("x", request.getHeader("Host"));
    }

    @Test
    void getRequestsHaveEmptyBody() throws IOException {
        assertEquals("", parse("GET / HTTP/1.1\r\n\r\n").getBody());
    }

    @Test
    void emptyConnectionReturnsNull() throws IOException {
        assertNull(parse(""));
    }

    @Test
    void malformedRequestLineIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("GARBAGE\r\n\r\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("GET no-leading-slash HTTP/1.1\r\n\r\n"));
    }

    @Test
    void invalidContentLengthIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("POST / HTTP/1.1\r\nContent-Length: abc\r\n\r\n"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n"));
    }

    @Test
    void oversizedBodyIsRejected() {
        int tooBig = HttpRequest.MAX_BODY_BYTES + 1;
        assertThrows(IllegalArgumentException.class,
                () -> parse("POST / HTTP/1.1\r\nContent-Length: " + tooBig + "\r\n\r\n"));
    }

    @Test
    void overlongHeaderLineIsRejected() {
        String hugeHeader = "X-Big: " + "a".repeat(HttpRequest.MAX_LINE_BYTES);
        assertThrows(IllegalArgumentException.class,
                () -> parse("GET / HTTP/1.1\r\n" + hugeHeader + "\r\n\r\n"));
    }
}
