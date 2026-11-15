#!/usr/bin/env bash
# ==============================================================================
# GCET Placement Hub - Root macOS Launch Shortcut
# Forwards execution to gcet-placement-hub-java/run-mac.sh
# ==============================================================================

set -e
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR/gcet-placement-hub-java"

if [ -f "./run-mac.sh" ]; then
    chmod +x "./run-mac.sh"
    exec ./run-mac.sh "$@"
else
    echo "[ERROR] gcet-placement-hub-java/run-mac.sh not found."
    exit 1
fi
