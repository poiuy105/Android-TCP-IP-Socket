#!/bin/bash

# Build script for Post Tell Me Android app with MQTT support

set -e

echo "=== Environment Check ==="
echo "ANDROID_HOME=$ANDROID_HOME"
echo "Java version:"
java -version

# Check if build tools and platform are available
if [ ! -f "$ANDROID_HOME/build-tools/34.0.0/aapt" ]; then
    echo "ERROR: aapt not found at $ANDROID_HOME/build-tools/34.0.0/aapt"
    ls -la "$ANDROID_HOME/build-tools/" 2>/dev/null || true
    exit 1
fi

if [ ! -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then
    echo "ERROR: android.jar not found at $ANDROID_HOME/platforms/android-34/android.jar"
    ls -la "$ANDROID_HOME/platforms/" 2>/dev/null || true
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
mkdir -p Server/libs

# Download MQTT dependencies if not present
echo "=== Downloading MQTT Dependencies ==="
MQTT_CLIENT_JAR="Server/libs/org.eclipse.paho.client.mqttv3-1.2.5.jar"
MQTT_ANDROID_JAR="Server/libs/org.eclipse.paho.android.service-1.1.1.jar"

if [ ! -f "$MQTT_CLIENT_JAR" ]; then
    echo "Downloading Paho MQTT Client..."
    wget -O "$MQTT_CLIENT_JAR" "https://repo1.maven.org/maven2/org/eclipse/paho/org.eclipse.paho.client.mqttv3/1.2.5/org.eclipse.paho.client.mqttv3-1.2.5.jar"
fi

if [ ! -f "$MQTT_ANDROID_JAR" ]; then
    echo "Downloading Paho MQTT Android Service..."
    wget -O "$MQTT_ANDROID_JAR" "https://repo1.maven.org/maven2/org/eclipse/paho/org.eclipse.paho.android.service/1.1.1/org.eclipse.paho.android.service-1.1.1.jar"
fi

# Copy local support libraries if available
if [ -f "Server/bin/dexedLibs/android-support-v4-0131180b4fa77264a63c4f561b6509c8.jar" ]; then
    echo "Using local support-v4 library"
    cp -f Server/bin/dexedLibs/android-support-v4-0131180b4fa77264a63c4f561b6509c8.jar Server/libs/android-support-v4.jar 2>/dev/null || true
fi

if [ -f "Server/bin/dexedLibs/android-support-v7-appcompat-542251fa48d244d9965069beee8f8ead.jar" ]; then
    echo "Using local support-v7-appcompat library"
    cp -f Server/bin/dexedLibs/android-support-v7-appcompat-542251fa48d244d9965069beee8f8ead.jar Server/libs/android-support-v7-appcompat.jar 2>/dev/null || true
fi

# Verify JAR files exist
echo "Checking dependencies..."
for jar in "$MQTT_CLIENT_JAR" "$MQTT_ANDROID_JAR"; do
    if [ ! -f "$jar" ]; then
        echo "ERROR: Required JAR not found: $jar"
        exit 1
    fi
done

echo "All dependencies ready"
ls -la Server/libs/

# Build classpath with all JARs
CLASSPATH="$ANDROID_JAR"
for jar in Server/libs/*.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

echo "Classpath: $CLASSPATH"

# Step 1: Compile resources
echo "=== Step 1: aapt compile resources ==="
$AAPT package -f -m -J Server/gen -S Server/res -M Server/AndroidManifest.xml -I "$ANDROID_JAR"

echo "Generated R.java files:"
find Server/gen -name "*.java" 2>/dev/null || true

# Step 2: Compile Java files
echo "=== Step 2: Compile Java files ==="
find Server/src -name "*.java" > Server/src_files.txt
find Server/gen -name "*.java" >> Server/src_files.txt

echo "Source files to compile:"
cat Server/src_files.txt

echo "Compiling Java files..."
$JAVAC -source 1.8 -target 1.8 -d Server/bin -cp "$CLASSPATH" @Server/src_files.txt 2>&1 || {
    echo "ERROR: Java compilation failed"
    exit 1
}

echo "Compiled class files:"
find Server/bin -name "*.class" | head -20

# Step 3: Create DEX files
echo "=== Step 3: Create DEX files ==="

# Collect all JAR files
JAR_FILES=""
for jar in Server/libs/*.jar; do
    if [ -f "$jar" ]; then
        JAR_FILES="$JAR_FILES $jar"
    fi
done

# Convert all class files and JARs to DEX in one pass
echo "Converting all classes and JARs to DEX..."
$ANDROID_HOME/build-tools/34.0.0/d8 --output Server/bin --lib "$ANDROID_JAR" \
    $(find Server/bin -name "*.class") \
    $JAR_FILES

echo "DEX files created:"
ls -la Server/bin/*.dex 2>/dev/null || {
    echo "ERROR: No DEX files created"
    exit 1
}

# Count DEX files
DEX_COUNT=$(ls Server/bin/*.dex 2>/dev/null | wc -l)
echo "Total DEX files: $DEX_COUNT"

# Step 4: Package APK
echo "=== Step 4: Package APK ==="
# Remove any existing files that might interfere
rm -f Server/bin/AndroidManifest.xml 2>/dev/null || true

# Package with aapt - include all DEX files
echo "Packaging APK..."
$AAPT package -f -M Server/AndroidManifest.xml -S Server/res -I "$ANDROID_JAR" -F Server/bin/PostTellMe.unaligned.apk Server/bin

if [ ! -f Server/bin/PostTellMe.unaligned.apk ]; then
    echo "ERROR: APK package not created"
    exit 1
fi

# List APK contents to verify DEX files are included
echo "APK contents (DEX files):"
$ANDROID_HOME/build-tools/34.0.0/aapt list Server/bin/PostTellMe.unaligned.apk | grep -E "\.dex$" || echo "No DEX files found in APK!"

# Step 5: Align APK
echo "=== Step 5: Align APK ==="
$ZIPALIGN -f 4 Server/bin/PostTellMe.unaligned.apk Server/bin/PostTellMe.apk

if [ ! -f Server/bin/PostTellMe.apk ]; then
    echo "ERROR: Aligned APK not created"
    exit 1
fi

# Step 6: Sign APK (debug keystore)
echo "=== Step 6: Sign APK ==="
mkdir -p "${HOME}/.android"

DEBUG_KEYSTORE="${HOME}/.android/debug.keystore"
if [ ! -f "$DEBUG_KEYSTORE" ]; then
    echo "Creating debug keystore..."
    keytool -genkey -v -keystore "$DEBUG_KEYSTORE" -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" -storepass android -keypass android
fi

echo "Signing APK..."
$APKSIGNER sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android --key-pass pass:android Server/bin/PostTellMe.apk

# Verify APK
echo "Verifying APK..."
$APKSIGNER verify Server/bin/PostTellMe.apk

# Clean up
rm -f Server/bin/PostTellMe.unaligned.apk
rm -f Server/src_files.txt

echo "=== Build completed successfully! ==="
echo "APK location: Server/bin/PostTellMe.apk"
ls -la Server/bin/PostTellMe.apk

# Show final APK contents
echo "=== Final APK DEX files ==="
$ANDROID_HOME/build-tools/34.0.0/aapt list Server/bin/PostTellMe.apk | grep -E "\.dex$"
