@echo off
echo ==========================================
echo      PROJECT ATLAS SERVER LAUNCHER
echo ==========================================

echo [1/3] Cleaning old plugin jars...
del plugins\ProjectAtlas*.jar /Q

echo [2/3] Deploying latest build...
copy "..\build\libs\ProjectAtlas-1.0-SNAPSHOT-shaded.jar" "plugins\ProjectAtlas.jar" /Y

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to copy plugin JAR! Build might be missing.
    echo Please run debug_build.bat in the project root first.
    pause
    exit /b
)

echo [3/3] Starting Server...
echo ==========================================
java -Xms2G -Xmx2G -jar server.jar nogui
pause
