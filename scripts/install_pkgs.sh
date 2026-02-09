#!/bin/bash
set -e

# 1. System Dependencies
# Skip apt updates and installs if key packages already exist
if ! command -v java >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1 || ! command -v gpg >/dev/null 2>&1; then
    echo "Installing missing system packages..."
    sudo apt-get update -qq
    sudo apt-get install -y -qq openjdk-21-jdk wget unzip lib32z1 curl gpg
else
    echo "System dependencies already present, skipping apt install."
fi

# 2. Parse Gradle Version from Wrapper Properties
PROPERTIES_FILE="${CLAUDE_PROJECT_DIR:-.}/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "Error: gradle-wrapper.properties not found at $PROPERTIES_FILE"
    exit 1
fi

# Extract the URL and version
DIST_URL=$(grep 'distributionUrl' "$PROPERTIES_FILE" | cut -d'=' -f2 | sed 's/\\//g')
GRADLE_VERSION=$(echo "$DIST_URL" | grep -oP 'gradle-\K[0-9.]+(?=-bin|-all)')

# 3. Manual Gradle Installation
GRADLE_INSTALL_BASE="/opt/gradle"
GRADLE_HOME="$GRADLE_INSTALL_BASE/gradle-$GRADLE_VERSION"

if [ ! -d "$GRADLE_HOME" ]; then
    echo "Installing Gradle $GRADLE_VERSION (not found at $GRADLE_HOME)..."
    wget -q "$DIST_URL" -O /tmp/gradle.zip
    sudo mkdir -p "$GRADLE_INSTALL_BASE"
    sudo unzip -q /tmp/gradle.zip -d "$GRADLE_INSTALL_BASE"
    rm /tmp/gradle.zip
else
    echo "Gradle $GRADLE_VERSION already installed at $GRADLE_HOME."
fi

# 4. GitHub CLI (Optimized for speed)
if ! command -v gh >/dev/null 2>&1; then
    echo "Installing GitHub CLI..."
    sudo mkdir -p -m 755 /etc/apt/keyrings
    if [ ! -f /etc/apt/keyrings/githubcli-archive-keyring.gpg ]; then
        wget -qO- https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo tee /etc/apt/keyrings/githubcli-archive-keyring.gpg > /dev/null
    fi
    sudo chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null
    sudo apt-get update -qq
    sudo apt-get install -y -qq gh
else
    echo "GitHub CLI already installed."
fi

# 5. Emscripten SDK (Pinned to 3.1.50)
EMSDK_DIR="$HOME/emsdk"
if [ ! -d "$EMSDK_DIR" ]; then
    echo "Cloning and installing Emscripten 3.1.50..."
    git clone -q https://github.com/emscripten-core/emsdk.git "$EMSDK_DIR"
    cd "$EMSDK_DIR"
    ./emsdk install 3.1.50 >/dev/null
    ./emsdk activate 3.1.50 >/dev/null
    cd - > /dev/null
else
    echo "Emscripten SDK found at $EMSDK_DIR."
fi

echo "Setup complete. Use 'gradle' command for builds and ensure 'emsdk_env.sh' is sourced for Wasm builds."
