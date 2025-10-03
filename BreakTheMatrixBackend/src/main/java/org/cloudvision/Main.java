package org.cloudvision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Break The Matrix Trading Bot Backend.
 * 
 * This is called by DesktopApplication after the JavaFX window is initialized.
 * The desktop app automatically launches the embedded browser with ui/index.html
 */
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
