package com.project.MultiThreadedWebServer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.MultiThreadedWebServer.GetMapping;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * HomeController provides basic endpoints for the server.
 * Demonstrates simple GET endpoints with HTML and JSON responses.
 */
@RestController
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GET / - Home page with welcome HTML
     */
    @GetMapping("/")
    public void home(HttpRequest request, HttpResponse response) {
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>HSpring Framework - by Harendra</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            padding: 20px;
                        }
                        .container {
                            background: white;
                            border-radius: 20px;
                            padding: 40px;
                            max-width: 800px;
                            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        }
                        h1 {
                            color: #667eea;
                            font-size: 2.5rem;
                            margin-bottom: 10px;
                        }
                        .subtitle {
                            color: #666;
                            font-size: 1.2rem;
                            margin-bottom: 30px;
                        }
                        h2 {
                            color: #333;
                            margin: 25px 0 15px;
                            padding-bottom: 10px;
                            border-bottom: 2px solid #667eea;
                        }
                        .endpoint {
                            background: #f8f9fa;
                            border-left: 4px solid #667eea;
                            padding: 12px 15px;
                            margin: 10px 0;
                            border-radius: 0 8px 8px 0;
                            font-family: 'Consolas', monospace;
                        }
                        .method {
                            display: inline-block;
                            padding: 3px 8px;
                            border-radius: 4px;
                            font-weight: bold;
                            margin-right: 10px;
                            font-size: 0.85rem;
                        }
                        .get { background: #61affe; color: white; }
                        .post { background: #49cc90; color: white; }
                        .put { background: #fca130; color: white; }
                        .delete { background: #f93e3e; color: white; }
                        .features {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                            gap: 15px;
                            margin-top: 20px;
                        }
                        .feature {
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            color: white;
                            padding: 15px;
                            border-radius: 10px;
                            text-align: center;
                        }
                        .feature-icon { font-size: 2rem; margin-bottom: 10px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🚀 HSpring Framework</h1>
                        <p class="subtitle">A lightweight Spring Boot implementation by Harendra</p>
                        
                        <h2>📚 API Documentation</h2>
                        
                        <div class="endpoint">
                            <span class="method get">GET</span>
                            <code>/api/users</code> - List all users
                        </div>
                        <div class="endpoint">
                            <span class="method get">GET</span>
                            <code>/api/users/{id}</code> - Get user by ID
                        </div>
                        <div class="endpoint">
                            <span class="method post">POST</span>
                            <code>/api/users</code> - Create new user
                        </div>
                        <div class="endpoint">
                            <span class="method put">PUT</span>
                            <code>/api/users/{id}</code> - Update user
                        </div>
                        <div class="endpoint">
                            <span class="method delete">DELETE</span>
                            <code>/api/users/{id}</code> - Delete user
                        </div>
                        <div class="endpoint">
                            <span class="method get">GET</span>
                            <code>/health</code> - Health check
                        </div>
                        
                        <h2>✨ Framework Features</h2>
                        <div class="features">
                            <div class="feature">
                                <div class="feature-icon">🔄</div>
                                <div>IoC Container</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">💉</div>
                                <div>Dependency Injection</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">🛣️</div>
                                <div>Dynamic Routing</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">🔗</div>
                                <div>Path Variables</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">🎯</div>
                                <div>Filters Chain</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">⚠️</div>
                                <div>Exception Handler</div>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """;
        response.html(html);
    }

    /**
     * GET /health - Health check endpoint
     */
    @GetMapping("/health")
    public void healthCheck(HttpRequest request, HttpResponse response) {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            health.put("version", "1.0.0");
            
            Map<String, Object> system = new HashMap<>();
            system.put("javaVersion", System.getProperty("java.version"));
            system.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            system.put("freeMemory", Runtime.getRuntime().freeMemory() / 1024 / 1024 + " MB");
            system.put("totalMemory", Runtime.getRuntime().totalMemory() / 1024 / 1024 + " MB");
            health.put("system", system);

            response.status(HttpResponse.OK).json(objectMapper.writeValueAsString(health));
        } catch (Exception e) {
            logger.error("Error in health check", e);
            response.sendError(HttpResponse.INTERNAL_SERVER_ERROR, "Health check failed");
        }
    }
}
