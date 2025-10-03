#!/bin/bash

# Run Break The Matrix as a Desktop Application
# This starts JavaFX browser window first, then Spring Boot in background

echo "🚀 Starting Break The Matrix Desktop Application"
echo "================================================"
echo ""
echo "✨ The browser window will open immediately"
echo "⏳ Spring Boot will load in the background"
echo ""

# Run the desktop application using JavaFX plugin (properly loads JavaFX runtime)
mvn clean javafx:run

echo ""
echo "Application closed."

