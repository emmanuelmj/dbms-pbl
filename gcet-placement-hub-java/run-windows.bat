@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo   GCET PLACEMENT HUB - Java 17 Desktop Edition
echo ============================================================

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java 17+ not found on PATH.
    pause
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Apache Maven not found on PATH.
    pause
    exit /b 1
)

if not exist ".env" if not exist "config.properties" if not exist "..\.env" (
    if exist ".env.example" (
        copy /Y ".env.example" ".env" >nul
        echo [INFO] Created .env from .env.example. Edit .env to set your MySQL DB_PASSWORD.
    )
)

call mvn clean compile
if errorlevel 1 (
    echo [ERROR] Maven build failed.
    pause
    exit /b 1
)

call mvn exec:java
endlocal
