<#
.SYNOPSIS
    Pre-flight check, build, and launch script for GCET PLACEMENT HUB (Java 17 Swing + MySQL JDBC) on Windows.
#>

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  GCET PLACEMENT HUB - Java 17 Desktop Edition Launcher" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Check Java 17+
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] 'java' command not found. Install JDK 17+ and add it to PATH." -ForegroundColor Red
    exit 1
}
$javaVerOutput = (& java -version 2>&1) -join "`n"
Write-Host "[OK] Java detected:`n$javaVerOutput" -ForegroundColor Green

# 2. Check Maven
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] 'mvn' (Apache Maven) not found. Install Maven 3.8+ and add it to PATH." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Maven detected: $((& mvn -version | Select-Object -First 1))" -ForegroundColor Green

# 3. Ensure external .env or config.properties exists
if (-not (Test-Path ".env") -and -not (Test-Path "config.properties") -and -not (Test-Path "..\.env")) {
    if (Test-Path ".env.example") {
        Copy-Item ".env.example" ".env"
        Write-Host "[ACTION REQUIRED] Created .env from .env.example." -ForegroundColor Yellow
        Write-Host "Please edit .env and set your local MySQL DB_PASSWORD, or configure credentials inside the app's 'MySQL Connection Settings' dialog." -ForegroundColor Yellow
    }
}

# 4. Compile & launch
Write-Host "`nCompiling Java 17 Swing application..." -ForegroundColor Cyan
mvn clean compile
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Maven compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nLaunching GCET PLACEMENT HUB..." -ForegroundColor Green
mvn exec:java
