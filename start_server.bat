@echo off
REM Start PingOwl Server with proper SQLite configuration
REM This script enables native access for SQLite JDBC driver

cd /d "%~dp0"

echo ===================================
echo PingOwl Server Startup
echo ===================================
echo.
echo Building project...
call mvn clean package -DskipTests -q

if errorlevel 1 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Starting server on port 4444...
echo (Server will use SQLite database: pingMe.db)
echo.

REM Run with native access enabled for SQLite
REM Suppress warnings about deprecated sun.misc.Unsafe
java --enable-native-access=ALL-UNNAMED ^
     -XX:+IgnoreUnrecognizedVMOptions ^
     -Dorg.sqlite.tmpdir=. ^
     --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.nio=ALL-UNNAMED ^
     -jar target/server.jar

pause
