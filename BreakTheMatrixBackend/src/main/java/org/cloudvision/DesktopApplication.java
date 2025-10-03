package org.cloudvision;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;

/**
 * Desktop Application - JavaFX First, Spring Boot Second
 * 
 * This starts the browser window immediately, then boots Spring Boot in the background.
 * Much better user experience - window appears instantly!
 */
public class DesktopApplication extends Application {
    
    private WebView webView;
    private WebEngine webEngine;
    private TextField addressBar;
    private Button backButton;
    private Button forwardButton;
    private Button refreshButton;
    private Label statusLabel;
    private ProgressBar loadingBar;
    private ConfigurableApplicationContext springContext;
    
    private static final int SERVER_PORT = 8080;
    private static final String WINDOW_TITLE = "Break The Matrix - Trading Dashboard";
    private static final int WINDOW_WIDTH = 1400;
    private static final int WINDOW_HEIGHT = 900;
    
    public static void main(String[] args) {
        System.out.println("🚀 Starting Break The Matrix Desktop Application...");
        System.out.println("📱 JavaFX will start first, then Spring Boot will load in background");
        
        // Launch JavaFX (this will call start() method)
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        System.out.println("🪟 Opening browser window...");
        
        // Create the main layout
        BorderPane root = new BorderPane();
        
        // Create WebView FIRST
        webView = new WebView();
        webEngine = webView.getEngine();
        
        // Enable JavaScript
        webEngine.setJavaScriptEnabled(true);
        
        // Set user agent
        webEngine.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        
        // Setup console logging bridge
        setupConsoleLogging();
        
        // Create toolbar
        HBox toolbar = createToolbar();
        root.setTop(toolbar);
        
        // Monitor loading state
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.RUNNING) {
                statusLabel.setText("Loading...");
                loadingBar.setVisible(true);
            } else if (newState == Worker.State.SUCCEEDED) {
                statusLabel.setText("Ready");
                loadingBar.setVisible(false);
                addressBar.setText(webEngine.getLocation());
                updateNavigationButtons();
            } else if (newState == Worker.State.FAILED) {
                statusLabel.setText("Failed to load");
                loadingBar.setVisible(false);
            }
        });
        
        // Handle location changes
        webEngine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
            addressBar.setText(newLoc);
        });
        
        // Add WebView to layout
        root.setCenter(webView);
        
        // Create status bar
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);
        
        // Load initial loading page
        loadingPage();
        
        // Create and show scene
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        primaryStage.setTitle(WINDOW_TITLE);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        System.out.println("✅ Browser window is now visible!");
        
        // Handle close request
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("🛑 Closing application...");
            if (springContext != null) {
                springContext.close();
            }
            Platform.exit();
            System.exit(0);
        });
        
        // Start Spring Boot in background thread
        startSpringBoot();
    }
    
    private void loadingPage() {
        String loadingHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Loading...</title>
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    }
                    .container {
                        text-align: center;
                        color: white;
                    }
                    h1 {
                        font-size: 3em;
                        margin-bottom: 20px;
                        animation: pulse 2s infinite;
                    }
                    .spinner {
                        width: 60px;
                        height: 60px;
                        border: 6px solid rgba(255, 255, 255, 0.3);
                        border-top: 6px solid white;
                        border-radius: 50%;
                        margin: 30px auto;
                        animation: spin 1s linear infinite;
                    }
                    .status {
                        font-size: 1.2em;
                        margin-top: 20px;
                    }
                    @keyframes spin {
                        0% { transform: rotate(0deg); }
                        100% { transform: rotate(360deg); }
                    }
                    @keyframes pulse {
                        0%, 100% { opacity: 1; }
                        50% { opacity: 0.7; }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🚀 Break The Matrix</h1>
                    <div class="spinner"></div>
                    <div class="status">Starting Spring Boot server...</div>
                    <div class="status" style="font-size: 0.9em; margin-top: 40px; opacity: 0.8;">
                        This will take a few seconds...
                    </div>
                </div>
            </body>
            </html>
        """;
        
        webEngine.loadContent(loadingHtml);
    }
    
    private void startSpringBoot() {
        Thread springThread = new Thread(() -> {
            try {
                System.out.println("⚙️  Starting Spring Boot...");
                
                // Start Spring Boot
                springContext = SpringApplication.run(Main.class);
                
                System.out.println("✅ Spring Boot started successfully!");
                
                // Wait a moment for everything to be ready
                Thread.sleep(1000);
                
                // Navigate to UI on JavaFX thread
                Platform.runLater(() -> {
                    System.out.println("🌐 Loading UI from ui/index.html");
                    
                    // Load the main UI
                    File uiFile = new File("ui/index.html");
                    if (uiFile.exists()) {
                        webEngine.load(uiFile.toURI().toString());
                    } else {
                        showError("UI file not found: ui/index.html");
                    }
                    
                    statusLabel.setText("Connected to Spring Boot");
                });
                
            } catch (Exception e) {
                System.err.println("❌ Failed to start Spring Boot:");
                e.printStackTrace();
                
                Platform.runLater(() -> {
                    statusLabel.setText("Error: Failed to start server");
                    showError("Failed to start Spring Boot server: " + e.getMessage());
                });
            }
        }, "Spring-Boot-Launcher");
        
        springThread.setDaemon(false);
        springThread.start();
    }
    
    private void showError(String message) {
        String errorHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Error</title>
                <style>
                    body {
                        margin: 0;
                        padding: 40px;
                        background: #f5f5f5;
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    }
                    .error-container {
                        max-width: 600px;
                        margin: 100px auto;
                        background: white;
                        padding: 40px;
                        border-radius: 10px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    h1 {
                        color: #e74c3c;
                    }
                    .message {
                        color: #333;
                        line-height: 1.6;
                    }
                </style>
            </head>
            <body>
                <div class="error-container">
                    <h1>❌ Error</h1>
                    <div class="message">%s</div>
                </div>
            </body>
            </html>
        """.formatted(message);
        
        webEngine.loadContent(errorHtml);
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(5);
        toolbar.setStyle("-fx-padding: 5; -fx-background-color: #e0e0e0;");
        
        // Back button
        backButton = new Button("←");
        backButton.setOnAction(e -> webEngine.getHistory().go(-1));
        backButton.setDisable(true);
        
        // Forward button
        forwardButton = new Button("→");
        forwardButton.setOnAction(e -> webEngine.getHistory().go(1));
        forwardButton.setDisable(true);
        
        // Refresh button
        refreshButton = new Button("⟳");
        refreshButton.setOnAction(e -> webEngine.reload());
        
        // Home button
        Button homeButton = new Button("🏠");
        homeButton.setOnAction(e -> navigateTo("http://localhost:" + SERVER_PORT));
        
        // Address bar
        addressBar = new TextField();
        addressBar.setPromptText("Enter URL...");
        HBox.setHgrow(addressBar, Priority.ALWAYS);
        addressBar.setOnAction(e -> navigateTo(addressBar.getText()));
        
        // Go button
        Button goButton = new Button("Go");
        goButton.setOnAction(e -> navigateTo(addressBar.getText()));
        
        toolbar.getChildren().addAll(
            backButton, 
            forwardButton, 
            refreshButton,
            homeButton,
            new Separator(), 
            addressBar, 
            goButton
        );
        
        return toolbar;
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setStyle("-fx-padding: 5; -fx-background-color: #f0f0f0;");
        
        statusLabel = new Label("Starting...");
        
        loadingBar = new ProgressBar();
        loadingBar.setPrefWidth(150);
        loadingBar.setVisible(false);
        
        statusBar.getChildren().addAll(statusLabel, loadingBar);
        return statusBar;
    }
    
    private void navigateTo(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        
        // Handle different URL formats
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
            webEngine.load(url);
        } else if (new File(url).exists()) {
            webEngine.load(new File(url).toURI().toString());
        } else {
            // Assume it's a domain and add http://
            webEngine.load("http://" + url);
        }
    }
    
    private void updateNavigationButtons() {
        backButton.setDisable(webEngine.getHistory().getCurrentIndex() <= 0);
        forwardButton.setDisable(
            webEngine.getHistory().getCurrentIndex() >= 
            webEngine.getHistory().getEntries().size() - 1
        );
    }
    
    private void setupConsoleLogging() {
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                try {
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("javaConsole", new ConsoleLogger());
                    
                    webEngine.executeScript(
                        "console.log = function(message) { javaConsole.log(message); };" +
                        "console.error = function(message) { javaConsole.error(message); };" +
                        "console.warn = function(message) { javaConsole.warn(message); };"
                    );
                } catch (Exception e) {
                    // Ignore errors in console setup
                }
            }
        });
    }
    
    public static class ConsoleLogger {
        public void log(Object message) {
            System.out.println("[Browser Console] " + message);
        }
        
        public void error(Object message) {
            System.err.println("[Browser Error] " + message);
        }
        
        public void warn(Object message) {
            System.out.println("[Browser Warning] " + message);
        }
    }
}

