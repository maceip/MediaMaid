#!/bin/bash
set -e

# 1. System Dependencies
echo "Installing system packages..."
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk wget unzip lib32z1 curl gpg

# 2. Parse Gradle Version from Wrapper Properties
PROPERTIES_FILE="$CLAUDE_PROJECT_DIR/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "Error: gradle-wrapper.properties not found at $PROPERTIES_FILE"
    exit 1
fi

# Extract the URL, removing backslash escapes for colons
DIST_URL=$(grep 'distributionUrl' "$PROPERTIES_FILE" | cut -d'=' -f2 | sed 's/\\//g')
# Extract version for folder naming (e.g., 8.5)
GRADLE_VERSION=$(echo "$DIST_URL" | grep -oP 'gradle-\K[0-9.]+(?=-bin|-all)')

echo "Detected Gradle Version: $GRADLE_VERSION"
echo "Fetching from: $DIST_URL"

# 3. Manual Gradle Installation
GRADLE_INSTALL_BASE="/opt/gradle"
GRADLE_HOME="$GRADLE_INSTALL_BASE/gradle-$GRADLE_VERSION"

if [ ! -d "$GRADLE_HOME" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    wget -q "$DIST_URL" -O /tmp/gradle.zip
    sudo mkdir -p "$GRADLE_INSTALL_BASE"
    sudo unzip -q /tmp/gradle.zip -d "$GRADLE_INSTALL_BASE"
    rm /tmp/gradle.zip
fi

# ... (Include your existing Android SDK and GH CLI logic here) ...

echo "Setup complete. Use 'gradle' command for builds."
