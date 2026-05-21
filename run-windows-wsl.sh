#!/bin/bash
# Run script for Windows + WSL environments

# Try to find javac/java in PATH first, then common WSL/Windows locations
find_java() {
    if command -v javac &>/dev/null; then
        echo "$(dirname $(command -v javac))"
        return
    fi
    # Common WSL apt-installed locations
    for dir in /usr/bin /usr/local/bin /usr/lib/jvm/java-21-openjdk-amd64/bin /usr/lib/jvm/java-17-openjdk-amd64/bin; do
        if [ -x "$dir/javac" ]; then
            echo "$dir"
            return
        fi
    done
    # Windows JDK accessible via /mnt/c
    for dir in \
        "/mnt/c/Program Files/Java" \
        "/mnt/c/Program Files/Eclipse Adoptium" \
        "/mnt/c/Program Files/Microsoft"; do
        local found
        found=$(find "$dir" -name "javac.exe" -maxdepth 4 2>/dev/null | head -1)
        if [ -n "$found" ]; then
            echo "$(dirname "$found")"
            return
        fi
    done
    echo ""
}

JAVA=$(find_java)

if [ -z "$JAVA" ]; then
    echo "Java not found. Installing OpenJDK 21 via apt..."
    sudo apt-get install -y openjdk-21-jdk-headless
    JAVA=$(find_java)
    if [ -z "$JAVA" ]; then
        echo "ERROR: Java installation failed. Install manually: sudo apt install openjdk-21-jdk"
        exit 1
    fi
fi

echo "Using Java from: $JAVA"

mkdir -p out
"$JAVA/javac" -d out $(find src -name "*.java") && cp -r res/. out/ && "$JAVA/java" -cp out Main
