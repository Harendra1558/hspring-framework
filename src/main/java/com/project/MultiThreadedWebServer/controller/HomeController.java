package com.project.MultiThreadedWebServer.controller;


import com.project.MultiThreadedWebServer.annotations.GetMapping;
import com.project.MultiThreadedWebServer.annotations.RestController;
import com.project.MultiThreadedWebServer.annotations.Value;
import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HomeController serves the documentation page.
 * 
 * CONCEPTS SHOWN:
 * 1. @RestController (no base path) → Routes start from "/"
 * 2. @Value → Injecting app name and version from application.properties
 * 3. HTML response → response.html() sets Content-Type to text/html
 * 
 * NOTE: This controller uses the void(req, resp) style on purpose,
 * because it returns HTML (not JSON). For JSON APIs, see UserController
 * which uses the Spring-like ResponseEntity return style.
 */
@RestController
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    /** Injected from application.properties: app.name=HSpring Framework */
    @Value("app.name")
    private String appName;

    /** Injected from application.properties: app.version=1.0.0 */
    @Value("app.version")
    private String appVersion;

    /**
     * GET / — Home page with API documentation.
     * 
     * Uses void(req, resp) style because this returns HTML, not JSON.
     * For JSON endpoints, see UserController which returns ResponseEntity.
     */
    @GetMapping("/")
    public void home(HttpRequest request, HttpResponse response) {
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s - v%s</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
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
                        h1 { color: #667eea; font-size: 2.5rem; margin-bottom: 10px; }
                        .subtitle { color: #666; font-size: 1.2rem; margin-bottom: 30px; }
                        h2 {
                            color: #333; margin: 25px 0 15px;
                            padding-bottom: 10px; border-bottom: 2px solid #667eea;
                        }
                        .endpoint {
                            background: #f8f9fa; border-left: 4px solid #667eea;
                            padding: 12px 15px; margin: 10px 0;
                            border-radius: 0 8px 8px 0; font-family: 'Consolas', monospace;
                        }
                        .method {
                            display: inline-block; padding: 3px 8px; border-radius: 4px;
                            font-weight: bold; margin-right: 10px; font-size: 0.85rem;
                        }
                        .get { background: #61affe; color: white; }
                        .post { background: #49cc90; color: white; }
                        .delete { background: #f93e3e; color: white; }
                        .features {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                            gap: 15px; margin-top: 20px;
                        }
                        .feature {
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                            color: white; padding: 15px; border-radius: 10px; text-align: center;
                        }
                        .feature-icon { font-size: 2rem; margin-bottom: 10px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🚀 %s</h1>
                        <p class="subtitle">Version %s — Learn Spring Boot Internals</p>
                        
                        <h2>📚 API Endpoints (Return ResponseEntity — like real Spring!)</h2>
                        <div class="endpoint">
                            <span class="method get">GET</span>
                            <code>/api/users/{id}</code> — ResponseEntity.ok(user)
                        </div>
                        <div class="endpoint">
                            <span class="method post">POST</span>
                            <code>/api/users</code> — ResponseEntity.created(user)
                        </div>
                        <div class="endpoint">
                            <span class="method delete">DELETE</span>
                            <code>/api/users/{id}</code> — Exception → @ControllerAdvice
                        </div>
                        
                        <h2>✨ Spring Boot Concepts Implemented</h2>
                        <div class="features">
                            <div class="feature">
                                <div class="feature-icon">🚀</div>
                                <div>@SpringBootApplication</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">🔄</div>
                                <div>IoC Container</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">💉</div>
                                <div>@Autowired DI</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">📦</div>
                                <div>ResponseEntity&lt;T&gt;</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">⚙️</div>
                                <div>@Configuration + @Bean</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">📋</div>
                                <div>@Value Properties</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">🔁</div>
                                <div>Bean Lifecycle</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">🏷️</div>
                                <div>@Qualifier</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">🛣️</div>
                                <div>Route Resolver</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">🎯</div>
                                <div>Filter Chain</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">⚠️</div>
                                <div>Exception Handler</div>
                            </div>
                            <div class="feature">
                                <div class="feature-icon">📡</div>
                                <div>Auto-Serialization</div>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(appName, appVersion, appName, appVersion);
        response.html(html);
    }
}
