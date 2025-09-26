#!/bin/bash

# Burp Suite Launcher Script with XWayland-satellite support
# Description: Launches Burp Suite with custom loader and Wayland compatibility
# Usage: ./launch-burp.sh

set -euo pipefail

# Project paths
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BURP_LOADER="$PROJECT_DIR/build/libs/burploader-fat.jar"
BURP_JAR="$HOME/.local/share/BurpSuite/burpsuite_pro_v2025.9.3.jar"

# XWayland satellite binary
XWAYLAND_BIN="xwayland-satellite"

echo "=== Burp Suite Launcher ==="

# Verify Java installation
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java not found in PATH. Please install OpenJDK 21+."
    exit 1
fi

# Check if loader exists, build if needed
if [ ! -f "$BURP_LOADER" ]; then
    echo "🔧 Building burploader-fat.jar..."
    cd "$PROJECT_DIR"
    ./gradlew shadowJar
    if [ ! -f "$BURP_LOADER" ]; then
        echo "❌ Error: Failed to build burploader-fat.jar"
        exit 1
    fi
fi

# Check if Burp Suite JAR exists
if [ ! -f "$BURP_JAR" ]; then
    echo "❌ Error: Burp Suite JAR not found at: $BURP_JAR"
    echo "💡 Run the keygen first to download Burp Suite"
    cd "$PROJECT_DIR"
    ./gradlew run
    exit 1
fi

# XWayland satellite support
XWAYLAND_PID=""
if command -v "$XWAYLAND_BIN" &> /dev/null; then
    echo "🚀 Starting XWayland-satellite..."
    "$XWAYLAND_BIN" &
    XWAYLAND_PID=$!
    sleep 3  # Give XWayland time to initialize

    # Cleanup on exit
    trap 'if [ -n "$XWAYLAND_PID" ] && kill -0 "$XWAYLAND_PID" 2>/dev/null; then
              echo "🛑 Stopping XWayland-satellite (PID $XWAYLAND_PID)"
              kill "$XWAYLAND_PID"
          fi' EXIT INT TERM
else
    echo "⚠️  XWayland-satellite not found, using native display"
fi

# XWayland-satellite optimized environment
export _JAVA_AWT_WM_NONREPARENTING=1
export DISPLAY=:1
export GDK_BACKEND=x11
export QT_QPA_PLATFORM=xcb

echo "📦 Loader: $(basename "$BURP_LOADER")"
echo "🎯 Target: $(basename "$BURP_JAR")"
echo "🖥️  Display: $DISPLAY"
echo ""
echo "🚀 Launching Burp Suite..."

# Launch Burp Suite with optimized flags
exec java \
    --add-opens=java.desktop/javax.swing=ALL-UNNAMED \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
    --add-opens=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
    --add-opens=java.base/jdk.internal.org.objectweb.asm.Opcodes=ALL-UNNAMED \
    --add-opens=java.base/sun.security.util=ALL-UNNAMED \
    -Dawt.toolkit=sun.awt.X11.XToolkit \
    -Dawt.useSystemAAFontSettings=on \
    -Dswing.aatext=true \
    -Dsun.java2d.xrender=true \
    -Dsun.java2d.pmoffscreen=false \
    -Djava.awt.headless=false \
    -Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel \
    -javaagent:"$BURP_LOADER" \
    -noverify \
    -jar "$BURP_JAR" "$@"