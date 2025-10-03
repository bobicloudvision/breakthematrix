@echo off
REM Build native desktop application for Break The Matrix Trading Bot (Windows)

echo Building Break The Matrix Trading Bot - Native Desktop App
echo ===========================================================

REM Configuration
set APP_NAME=Break The Matrix
set APP_VERSION=1.0.0
set MAIN_JAR=target\BreakTheMatrix.jar
set MAIN_CLASS=org.cloudvision.Main

REM Step 1: Build the JAR
echo.
echo Step 1: Building Spring Boot JAR...
call mvn clean package -DskipTests

if not exist "%MAIN_JAR%" (
    echo Error: JAR file not found at %MAIN_JAR%
    exit /b 1
)

echo JAR built successfully

REM Step 2: Create native installer using jpackage
echo.
echo Step 2: Creating Windows installer...

jpackage ^
    --input target ^
    --name "%APP_NAME%" ^
    --main-jar BreakTheMatrix.jar ^
    --main-class %MAIN_CLASS% ^
    --type msi ^
    --app-version %APP_VERSION% ^
    --vendor "CloudVision" ^
    --description "Advanced Trading Bot with Embedded Browser" ^
    --copyright "Copyright (c) 2025" ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut ^
    --java-options "-Xmx2g" ^
    --java-options "-Dspring.profiles.active=prod"

echo.
echo Build completed successfully!
echo.
echo Installation Instructions:
echo -------------------------
echo 1. Double-click the .msi installer
echo 2. Follow the installation wizard
echo 3. Launch from Start menu
echo.
echo Note: The application will run as a standalone desktop app
echo       with an embedded browser. No need to install Java separately!

pause

