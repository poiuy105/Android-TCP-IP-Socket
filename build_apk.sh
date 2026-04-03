#!/bin/bash

# Build script for Android TCP Server app

echo "=== Environment Check ==="
echo "ANDROID_HOME=$ANDROID_HOME"

# Check if build tools and platform are available
if [ ! -f "$ANDROID_HOME/build-tools/34.0.0/aapt" ]; then
    echo "ERROR: aapt not found"
    exit 1
fi

if [ ! -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then
    echo "ERROR: android.jar not found"
    exit 1
fi

# Define paths
AAPT="$ANDROID_HOME/build-tools/34.0.0/aapt"
JAVAC="javac"
DX="$ANDROID_HOME/build-tools/34.0.0/dx"
ZIPALIGN="$ANDROID_HOME/build-tools/34.0.0/zipalign"
APKSIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"

# Create output directories
mkdir -p Server/bin
mkdir -p Server/gen

# Step 1: Compile resources
echo "=== Step 1: aapt compile resources ==="
$AAPT package -f -m -J Server/gen -S Server/res -M Server/AndroidManifest.xml -I "$ANDROID_JAR"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile resources"
    exit 1
fi

# Step 2: Compile Java files
echo "=== Step 2: Compile Java files ==="
find Server/src -name "*.java" > Server/src_files.txt
find Server/gen -name "*.java" >> Server/src_files.txt

$JAVAC -d Server/bin -cp "$ANDROID_JAR" @Server/src_files.txt

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile Java files"
    exit 1
fi

# Step 3: Create DEX file
echo "=== Step 3: Create DEX file ==="
$DX --dex --output=Server/bin/classes.dex Server/bin

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to create DEX file"
    exit 1
fi

# Step 4: Package APK
echo "=== Step 4: Package APK ==="
$AAPT package -f -M Server/AndroidManifest.xml -S Server/res -I "$ANDROID_JAR" -F Server/bin/Server.unaligned.apk Server/bin

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to package APK"
    exit 1
fi

# Step 5: Align APK
echo "=== Step 5: Align APK ==="
$ZIPALIGN -f 4 Server/bin/Server.unaligned.apk Server/bin/Server.apk

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to align APK"
    exit 1
fi

# Step 6: Sign APK (debug keystore)
echo "=== Step 6: Sign APK ==="
DEBUG_KEYSTORE="~/.android/debug.keystore"
if [ ! -f "$DEBUG_KEYSTORE" ]; then
    echo "Creating debug keystore..."
    keytool -genkey -v -keystore "$DEBUG_KEYSTORE" -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" -storepass android -keypass android
fi

$APKSIGNER sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android --key-pass pass:android --out Server/bin/Server.signed.apk Server/bin/Server.apk

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to sign APK"
    exit 1
fi

# Clean up
mv Server/bin/Server.signed.apk Server/bin/Server.apk
rm Server/bin/Server.unaligned.apk
rm Server/src_files.txt

echo "=== Build completed successfully! ==="
echo "APK location: Server/bin/Server.apk"
