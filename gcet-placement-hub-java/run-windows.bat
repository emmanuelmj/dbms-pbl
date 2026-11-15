@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ========================================================================
echo   GCET PLACEMENT HUB - Java 17 Desktop (MongoDB Atlas Edition)
echo   Platform: Windows 10/11 - Geethanjali College of Engineering ^& Tech
echo ========================================================================

:: 1. Verify Java is installed or locate candidate JDK 17+
where java >nul 2>nul
if errorlevel 1 (
    if exist "%USERPROFILE%\.antigravity\extensions\redhat.java-1.12.0-win32-x64\jre\17.0.4.1-win32-x86_64\bin\java.exe" (
        set "JAVA_HOME=%USERPROFILE%\.antigravity\extensions\redhat.java-1.12.0-win32-x64\jre\17.0.4.1-win32-x86_64"
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    ) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-17"
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    ) else if exist "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17"
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    ) else (
        echo [ERROR] Java 17+ not found on PATH or known JDK locations.
        echo Please install OpenJDK 17 or higher and ensure java is in PATH.
        pause
        exit /b 1
    )
)

:: 2. Auto-copy .env.example to .env if .env is missing
if not exist ".env" if not exist "config.properties" if not exist "..\.env" (
    if exist ".env.example" (
        copy /Y ".env.example" ".env" >nul
        echo [INFO] Created .env from .env.example. Edit .env to set your MONGO_URI if using Atlas.
    )
)

:: 3. Select Maven executable (wrapper mvnw.cmd if present, else system mvn)
set "MAVEN_CMD=%~dp0mvnw.cmd"
if not exist "%~dp0mvnw.cmd" (
    where mvn >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] Neither mvnw.cmd nor system Apache Maven found on PATH.
        pause
        exit /b 1
    )
    set "MAVEN_CMD=mvn"
)

:: 4. Launch compilation if needed, then run application
if not exist "%~dp0target\classes\edu\gcet\placementhub\Main.class" (
    echo [INFO] Compiling Java 17 sources...
    call "%MAVEN_CMD%" compile
    if errorlevel 1 (
        echo [ERROR] Compilation failed.
        pause
        exit /b 1
    )
)

echo [INFO] Launching GCET Placement Hub desktop application...
call "%MAVEN_CMD%" exec:java
if errorlevel 1 (
    echo [ERROR] Application terminated with an error code.
    pause
    exit /b 1
)

endlocal
