#!/bin/bash

# Build script for Post Tell Me Android app with MQTT support

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
mkdir -p Server/libs

# Download MQTT dependencies if not present
echo "=== Downloading MQTT Dependencies ==="
MQTT_CLIENT_JAR="Server/libs/org.eclipse.paho.client.mqttv3-1.2.5.jar"
MQTT_ANDROID_JAR="Server/libs/org.eclipse.paho.android.service-1.1.1.jar"

if [ ! -f "$MQTT_CLIENT_JAR" ]; then
    echo "Downloading Paho MQTT Client..."
    wget -q -O "$MQTT_CLIENT_JAR" "https://repo1.maven.org/maven2/org/eclipse/paho/org.eclipse.paho.client.mqttv3/1.2.5/org.eclipse.paho.client.mqttv3-1.2.5.jar"
    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to download Paho MQTT Client"
        exit 1
    fi
fi

if [ ! -f "$MQTT_ANDROID_JAR" ]; then
    echo "Downloading Paho MQTT Android Service..."
    wget -q -O "$MQTT_ANDROID_JAR" "https://repo1.maven.org/maven2/org/eclipse/paho/org.eclipse.paho.android.service/1.1.1/org.eclipse.paho.android.service-1.1.1.jar"
    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to download Paho MQTT Android Service"
        exit 1
    fi
fi

# Verify JAR files exist
if [ ! -f "$MQTT_CLIENT_JAR" ] || [ ! -f "$MQTT_ANDROID_JAR" ]; then
    echo "ERROR: MQTT JAR files not found after download"
    exit 1
fi

echo "MQTT dependencies ready"

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

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile resources"
    exit 1
fi

# Step 2: Compile Java files
echo "=== Step 2: Compile Java files ==="
find Server/src -name "*.java" > Server/src_files.txt
find Server/gen -name "*.java" >> Server/src_files.txt

if [ ! -s Server/src_files.txt ]; then
    echo "ERROR: No Java source files found"
    exit 1
fi

echo "Compiling Java files..."
$JAVAC -source 1.8 -target 1.8 -d Server/bin -cp "$CLASSPATH" @Server/src_files.txt

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile Java files"
    cat Server/src_files.txt
    exit 1
fi

# Step 3: Create DEX file
echo "=== Step 3: Create DEX file ==="
# Find all class files and JAR files
find Server/bin -name "*.class" > Server/class_files.txt

if [ ! -s Server/class_files.txt ]; then
    echo "ERROR: No class files found"
    exit 1
fi

# Convert class files to DEX using d8
echo "Converting to DEX..."
$ANDROID_HOME/build-tools/34.0.0/d8 --output Server/bin --lib "$ANDROID_JAR" @Server/class_files.txt Server/libs/*.jar

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to create DEX file"
    exit 1
fi

# Step 4: Package APK
echo "=== Step 4: Package APK ==="
# Remove duplicate AndroidManifest.xml from bin directory if exists
if [ -f Server/bin/AndroidManifest.xml ]; then
    rm Server/bin/AndroidManifest.xml
fi

# Package with aapt
$AAPT package -f -M Server/AndroidManifest.xml -S Server/res -I "$ANDROID_JAR" -F Server/bin/PostTellMe.unaligned.apk Server/bin

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to package APK"
    exit 1
fi

# Step 5: Align APK
echo "=== Step 5: Align APK ==="
$ZIPALIGN -f 4 Server/bin/PostTellMe.unaligned.apk Server/bin/PostTellMe.apk

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to align APK"
    exit 1
fi

# Step 6: Sign APK (debug keystore)
echo "=== Step 6: Sign APK ==="
# Create .android directory if it doesn't exist
mkdir -p "${HOME}/.android"

DEBUG_KEYSTORE="${HOME}/.android/debug.keystore"
if [ ! -f "$DEBUG_KEYSTORE" ]; then
    echo "Creating debug keystore..."
    keytool -genkey -v -keystore "$DEBUG_KEYSTORE" -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" -storepass android -keypass android
fi

# Use apksigner to sign the APK
$APKSIGNER sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android --key-pass pass:android Server/bin/PostTellMe.apk

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to sign APK"
    exit 1
fi

# Clean up
rm -f Server/bin/PostTellMe.unaligned.apk
rm -f Server/src_files.txt
rm -f Server/class_files.txt

echo "=== Build completed successfully! ==="
echo "APK location: Server/bin/PostTellMe.apk"
ls -la Server/bin/PostTellMe.apk
