#!/bin/bash

# Build native desktop application for Break The Matrix Trading Bot
# This script creates native installers for macOS, Windows, or Linux

echo "🚀 Building Break The Matrix Trading Bot - Native Desktop App"
echo "============================================================"

# Configuration
APP_NAME="Break The Matrix"
APP_VERSION="1.0.0"
MAIN_JAR="target/BreakTheMatrix.jar"
MAIN_CLASS="org.cloudvision.DesktopApplication"
ICON_PATH="app-icon.icns"  # For macOS (.icns), Windows (.ico), Linux (.png)

# Detect OS
OS_TYPE=$(uname -s)
echo "📦 Detected OS: $OS_TYPE"

# Step 1: Build the JAR
echo ""
echo "Step 1: Building Spring Boot JAR..."
mvn clean package -DskipTests

if [ ! -f "$MAIN_JAR" ]; then
    echo "❌ Error: JAR file not found at $MAIN_JAR"
    exit 1
fi

echo "✅ JAR built successfully"

# Step 2: Create native installer using jpackage
echo ""
echo "Step 2: Creating native installer..."

case "$OS_TYPE" in
    Darwin*)
        # macOS
        echo "🍎 Building macOS application..."
        
        jpackage \
            --input target \
            --name "$APP_NAME" \
            --main-jar BreakTheMatrix.jar \
            --type dmg \
            --app-version "$APP_VERSION" \
            --vendor "CloudVision" \
            --description "Advanced Trading Bot with Embedded Browser" \
            --copyright "Copyright © 2025" \
            --mac-package-name "BreakTheMatrix" \
            --java-options '-Xmx2g' \
            --java-options '-Dspring.profiles.active=prod'
        
        echo "✅ macOS DMG created successfully!"
        echo "📦 Installer: Break The Matrix-$APP_VERSION.dmg"
        ;;
        
    Linux*)
        # Linux
        echo "🐧 Building Linux application..."
        
        jpackage \
            --input target \
            --name "$APP_NAME" \
            --main-jar BreakTheMatrix.jar \
            --main-class "$MAIN_CLASS" \
            --type deb \
            --app-version "$APP_VERSION" \
            --vendor "CloudVision" \
            --description "Advanced Trading Bot with Embedded Browser" \
            --copyright "Copyright © 2025" \
            --linux-package-name "breakthematrix" \
            --linux-shortcut \
            --java-options '-Xmx2g' \
            --java-options '-Dspring.profiles.active=prod'
        
        echo "✅ Linux DEB package created successfully!"
        echo "📦 Installer: break-the-matrix_$APP_VERSION-1_amd64.deb"
        ;;
        
    MINGW*|MSYS*|CYGWIN*)
        # Windows
        echo "🪟 Building Windows application..."
        
        jpackage \
            --input target \
            --name "$APP_NAME" \
            --main-jar BreakTheMatrix.jar \
            --main-class "$MAIN_CLASS" \
            --type msi \
            --app-version "$APP_VERSION" \
            --vendor "CloudVision" \
            --description "Advanced Trading Bot with Embedded Browser" \
            --copyright "Copyright © 2025" \
            --win-dir-chooser \
            --win-menu \
            --win-shortcut \
            --java-options '-Xmx2g' \
            --java-options '-Dspring.profiles.active=prod'
        
        echo "✅ Windows MSI installer created successfully!"
        echo "📦 Installer: Break The Matrix-$APP_VERSION.msi"
        ;;
        
    *)
        echo "❌ Unsupported operating system: $OS_TYPE"
        exit 1
        ;;
esac

echo ""
echo "🎉 Build completed successfully!"
echo ""
echo "Installation Instructions:"
echo "-------------------------"
case "$OS_TYPE" in
    Darwin*)
        echo "1. Open the .dmg file"
        echo "2. Drag 'Break The Matrix' to Applications folder"
        echo "3. Launch from Applications"
        ;;
    Linux*)
        echo "1. Install with: sudo dpkg -i break-the-matrix_$APP_VERSION-1_amd64.deb"
        echo "2. Launch from applications menu or run: breakthematrix"
        ;;
    MINGW*|MSYS*|CYGWIN*)
        echo "1. Double-click the .msi installer"
        echo "2. Follow the installation wizard"
        echo "3. Launch from Start menu"
        ;;
esac

echo ""
echo "📝 Note: The application will run as a standalone desktop app"
echo "   with an embedded browser. No need to install Java separately!"

