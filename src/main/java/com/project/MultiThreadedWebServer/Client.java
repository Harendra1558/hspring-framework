package com.project.MultiThreadedWebServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Test Client for the MiniSpring Web Server.
 * Demonstrates how to make HTTP requests to test all API endpoints.
 * 
 * This client shows:
 * - How to construct valid HTTP requests
 * - How to handle HTTP responses
 * - Testing GET, POST, PUT, DELETE methods
 */
public class Client {

    private static final int PORT = 8080;
    private static final String HOST = "localhost";
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) {
        Client client = new Client();
        
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║           HSpring API Test Client - by Harendra            ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
        
        // Run all tests
        client.runAllTests();
    }

    public void runAllTests() {
        try {
            // Wait a moment for server to be ready
            Thread.sleep(1000);
            
            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 1: GET / (Home Page)");
            System.out.println("═".repeat(60));
            sendGet("/");

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 2: GET /health (Health Check)");
            System.out.println("═".repeat(60));
            sendGet("/health");

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 3: GET /api/users (List Users)");
            System.out.println("═".repeat(60));
            sendGet("/api/users");

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 4: GET /api/users?limit=2 (List Users with Limit)");
            System.out.println("═".repeat(60));
            sendGet("/api/users?limit=2");

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 5: GET /api/users/1 (Get User by ID)");
            System.out.println("═".repeat(60));
            sendGet("/api/users/1");

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 6: POST /api/users (Create User)");
            System.out.println("═".repeat(60));
            String newUserJson = "{\"name\": \"Alice Wonder\", \"email\": \"alice@example.com\"}";
            sendPost("/api/users", newUserJson);

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 7: PUT /api/users/1 (Update User)");
            System.out.println("═".repeat(60));
            String updateJson = "{\"name\": \"John Updated\", \"email\": \"john.updated@example.com\"}";
            sendPut("/api/users/1", updateJson);

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 8: GET /api/users/1 (Verify Update)");
            System.out.println("═".repeat(60));
            sendGet("/api/users/1");

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 9: DELETE /api/users/3 (Delete User)");
            System.out.println("═".repeat(60));
            sendDelete("/api/users/3");

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 10: GET /api/users (Verify Deletion)");
            System.out.println("═".repeat(60));
            sendGet("/api/users");

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 11: GET /api/users/999 (Not Found)");
            System.out.println("═".repeat(60));
            sendGet("/api/users/999");

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 12: POST /api/users (Validation Error - Missing Email)");
            System.out.println("═".repeat(60));
            String invalidJson = "{\"name\": \"Invalid User\"}";
            sendPost("/api/users", invalidJson);

            System.out.println("\n" + "═".repeat(60));
            System.out.println("TEST 13: GET /nonexistent (404 Not Found)");
            System.out.println("═".repeat(60));
            sendGet("/nonexistent");

            System.out.println("\n\n" + "═".repeat(60));
            System.out.println("                  ALL TESTS COMPLETED!");
            System.out.println("═".repeat(60));
            
        } catch (InterruptedException e) {
            logger.error("Test interrupted", e);
        }
    }

    /**
     * Sends a GET request.
     */
    public void sendGet(String path) {
        sendRequest("GET", path, null);
    }

    /**
     * Sends a POST request with JSON body.
     */
    public void sendPost(String path, String jsonBody) {
        sendRequest("POST", path, jsonBody);
    }

    /**
     * Sends a PUT request with JSON body.
     */
    public void sendPut(String path, String jsonBody) {
        sendRequest("PUT", path, jsonBody);
    }

    /**
     * Sends a DELETE request.
     */
    public void sendDelete(String path) {
        sendRequest("DELETE", path, null);
    }

    /**
     * Sends an HTTP request and prints the response.
     */
    private void sendRequest(String method, String path, String body) {
        try {
            InetAddress serverAddress = InetAddress.getByName(HOST);

            try (Socket socket = new Socket(serverAddress, PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), false);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Build the request
                StringBuilder request = new StringBuilder();
                request.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
                request.append("Host: ").append(HOST).append("\r\n");
                request.append("Connection: close\r\n");
                request.append("Accept: application/json\r\n");

                if (body != null && !body.isEmpty()) {
                    request.append("Content-Type: application/json\r\n");
                    request.append("Content-Length: ").append(body.getBytes().length).append("\r\n");
                }

                request.append("\r\n");

                if (body != null && !body.isEmpty()) {
                    request.append(body);
                }

                // Send the request
                out.print(request);
                out.flush();

                System.out.println("➡ Request:");
                System.out.println("   " + method + " " + path);
                if (body != null) {
                    System.out.println("   Body: " + body);
                }

                // Read the response
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line).append("\n");
                }

                System.out.println("\n⬅ Response:");
                // Pretty print the response
                String[] lines = response.toString().split("\n");
                for (String l : lines) {
                    System.out.println("   " + l);
                }

            }
        } catch (IOException e) {
            System.err.println("❌ Error: " + e.getMessage());
            System.err.println("   Make sure the server is running on port " + PORT);
        }
    }

    /**
     * Load test: Send multiple concurrent requests.
     */
    public void runLoadTest(int numRequests) {
        logger.info("Starting load test with {} requests", numRequests);
        long startTime = System.currentTimeMillis();

        Thread[] threads = new Thread[numRequests];
        for (int i = 0; i < numRequests; i++) {
            final int requestId = i;
            threads[i] = new Thread(() -> {
                try {
                    InetAddress serverAddress = InetAddress.getByName(HOST);
                    try (Socket socket = new Socket(serverAddress, PORT);
                         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                        out.print("GET /health HTTP/1.1\r\n");
                        out.print("Host: " + HOST + "\r\n");
                        out.print("Connection: close\r\n");
                        out.print("\r\n");
                        out.flush();

                        // Read response status
                        String statusLine = in.readLine();
                        if (statusLine != null && statusLine.contains("200")) {
                            logger.debug("Request {} successful", requestId);
                        } else {
                            logger.warn("Request {} failed: {}", requestId, statusLine);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Request {} error: {}", requestId, e.getMessage());
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Load test completed: {} requests in {} ms", numRequests, elapsed);
        logger.info("Throughput: {} requests/second", (numRequests * 1000.0 / elapsed));
    }
}
