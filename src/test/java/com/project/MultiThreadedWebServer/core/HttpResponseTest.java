package com.project.MultiThreadedWebServer.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpResponseTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final HttpResponse response = new HttpResponse(out);

    private String written() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void writesStatusLineHeadersAndBody() {
        response.status(HttpResponse.CREATED).header("X-Test", "1").json("{\"ok\":true}");

        String raw = written();
        assertTrue(raw.startsWith("HTTP/1.1 201 Created\r\n"), raw);
        assertTrue(raw.contains("Content-Type: application/json\r\n"));
        assertTrue(raw.contains("X-Test: 1\r\n"));
        assertTrue(raw.endsWith("\r\n\r\n{\"ok\":true}"));
    }

    @Test
    void contentLengthCountsUtf8Bytes() {
        String body = "Zoë 日本";
        response.text(body);

        int expected = body.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(written().contains("Content-Length: " + expected + "\r\n"), written());
        assertTrue(written().endsWith(body));
    }

    @Test
    void cannotSendTwice() {
        response.text("first");
        assertTrue(response.isHeadersSent());
        assertThrows(IllegalStateException.class, () -> response.text("second"));
    }

    @Test
    void sendErrorProducesValidEscapedJson() {
        response.sendError(HttpResponse.BAD_REQUEST, "Name \"Bob\" is invalid");

        String raw = written();
        assertTrue(raw.startsWith("HTTP/1.1 400 Bad Request"));
        assertTrue(raw.endsWith("{\"error\": \"Bad Request\", \"message\": \"Name \\\"Bob\\\" is invalid\", \"status\": 400}"), raw);
    }

    @Test
    void escapeJsonHandlesControlCharacters() {
        assertEquals("a\\\\b\\n\\t\\u0001", HttpResponse.escapeJson("a\\b\n\t\u0001"));
        assertEquals("", HttpResponse.escapeJson(null));
    }
}
