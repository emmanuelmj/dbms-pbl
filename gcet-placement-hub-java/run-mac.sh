#!/usr/bin/env bash
# ==============================================================================
# GCET Placement Hub - macOS Launch Script
# Geethanjali College of Engineering & Technology (GCET Hyderabad)
# Cross-Platform Runner with Java 17+ Check, Dotenv Auto-Setup, & Maven Wrapper
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================================================"
echo "  GCET PLACEMENT HUB - Java 17 Desktop (MongoDB Atlas Edition)"
echo "  Platform: macOS (Darwin) | Retina / HiDPI Display Aware"
echo "========================================================================"

# 1. Verify Java 17+ is installed
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java runtime not found on PATH."
    echo "Please install OpenJDK 17 or higher (e.g., via Homebrew: brew install openjdk@17)"
    exit 1
fi

JAVA_VERSION_RAW=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
JAVA_MAJOR=$(echo "$JAVA_VERSION_RAW" | awk -F. '{if ($1 == "1") print $2; else print $1}')

if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 17 ]; then
    echo "[ERROR] Java 17 or higher is required to run GCET Placement Hub."
    echo "Detected version: $JAVA_VERSION_RAW (major: $JAVA_MAJOR)"
    echo "Please upgrade your JDK: brew install openjdk@17"
    exit 1
fi

echo "[INFO] Java runtime verified: OpenJDK $JAVA_VERSION_RAW"

# 2. Auto-copy .env.example to .env if .env does not exist
if [ ! -f ".env" ] && [ ! -f "config.properties" ] && [ ! -f "../.env" ]; then
    if [ -f ".env.example" ]; then
        echo "[INFO] No .env found. Auto-generating .env from .env.example..."
        cp ".env.example" ".env"
        echo "[INFO] .env created successfully. You can edit .env to customize your MONGO_URI."
    fi
fi

# 3. Ensure Maven Wrapper has executable permissions
if [ -f "./mvnw" ]; then
    chmod +x "./mvnw"
fi

# 4. macOS Retina / HiDPI Swing rendering properties
export MAVEN_OPTS="${MAVEN_OPTS} -Dapple.laf.useScreenMenuBar=true -Dapple.awt.application.name=\"GCET Placement Hub\" -Dsun.java2d.metal=true"

# 5. Compile and Launch Application
echo "[INFO] Compiling and starting GCET Placement Hub via Maven Wrapper..."
./mvnw clean compile exec:java
