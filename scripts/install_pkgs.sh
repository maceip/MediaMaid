#!/bin/bash
set -e

# 1. System Dependencies & GitHub CLI
echo "Installing system packages and GitHub CLI..."
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk wget unzip lib32z1 curl gpg

# Setup GH CLI repo
sudo mkdir -p -m 755 /etc/apt/keyrings
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/etc/apt/keyrings/githubcli-archive-keyring.gpg
sudo chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null

sudo apt-get update
sudo apt-get install -y gh

# 2. Android SDK Setup
export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "Downloading Android Command Line Tools..."
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline_tools.zip
    unzip -q /tmp/cmdline_tools.zip -d "$ANDROID_HOME/cmdline-tools"
    # Essential move for sdkmanager to detect its own root
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm /tmp/cmdline_tools.zip
fi

# 3. Install SDK 36 Components
echo "Accepting licenses and installing SDK 36..."
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0-rc1"

echo "Setup complete."
