<#
.SYNOPSIS
    Pre-flight check, build, and launch script for GCET PLACEMENT HUB (Java 17 Swing + MongoDB Atlas) on Windows.
#>

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  GCET PLACEMENT HUB - Java 17 Desktop Edition (MongoDB)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Check or locate Java 17+
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    # java is not on PATH: try JAVA_HOME, then the standard JDK install folders (Temurin, Oracle, Microsoft, Zulu, Corretto)
    $candidateJdks = @()
    if ($env:JAVA_HOME) { $candidateJdks += $env:JAVA_HOME }
    foreach ($root in @("C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java", "C:\Program Files\Microsoft", "C:\Program Files\Zulu", "C:\Program Files\Amazon Corretto")) {
        if (Test-Path $root) {
            $candidateJdks += (Get-ChildItem $root -Directory | Where-Object { $_.Name -match "jdk" } | Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
        }
    }
    foreach ($cand in $candidateJdks) {
        if (Test-Path "$cand\bin\java.exe") {
            $env:JAVA_HOME = $cand
            $env:PATH = "$cand\bin;$env:PATH"
            Write-Host "[INFO] Configured JAVA_HOME from $cand" -ForegroundColor Yellow
            break
        }
    }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] 'java' command not found. Install JDK 17+ and add it to PATH." -ForegroundColor Red
    exit 1
}
$javaVerOutput = (& java -version 2>&1) -join "`n"
Write-Host "[OK] Java detected:`n$javaVerOutput" -ForegroundColor Green

# 2. Select Maven executable (mvnw.cmd or system mvn)
$mavenCmd = ".\mvnw.cmd"
if (-not (Test-Path "mvnw.cmd")) {
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        $mavenCmd = "mvn"
    } else {
        Write-Host "[ERROR] Neither Maven Wrapper (mvnw.cmd) nor system 'mvn' was found." -ForegroundColor Red
        exit 1
    }
}
Write-Host "[OK] Using build tool: $mavenCmd" -ForegroundColor Green

# 3. Ensure external .env or config.properties exists
if (-not (Test-Path ".env") -and -not (Test-Path "config.properties") -and -not (Test-Path "..\.env")) {
    if (Test-Path ".env.example") {
        Copy-Item ".env.example" ".env"
        Write-Host "[ACTION REQUIRED] Created .env from .env.example." -ForegroundColor Yellow
        Write-Host "Defaulting to local MongoDB Community (mongodb://localhost:27017)." -ForegroundColor Yellow
        Write-Host "To use Atlas, edit .env and update MONGO_URI." -ForegroundColor Yellow
    }
}

# 4. Compile & launch
Write-Host "`nCompiling Java 17 Swing application with MongoDB driver..." -ForegroundColor Cyan
& $mavenCmd clean compile
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Maven compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nLaunching GCET PLACEMENT HUB..." -ForegroundColor Green
& $mavenCmd exec:java
