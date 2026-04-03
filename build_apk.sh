#!/bin/bash
set -e

cd Server
mkdir -p build/outputs/apk/debug bin/classes gen

echo "=== Environment Check ==="
echo "ANDROID_HOME=$ANDROID_HOME"
ls -la "$ANDROID_HOME/build-tools/34.0.0/aapt" || echo "aapt NOT FOUND"
ls -la "$ANDROID_HOME/platforms/android-34/android.jar" || echo "android.jar NOT FOUND"
ls -la bin/dexedLibs/ || echo "dexedLibs NOT FOUND"

echo ""
echo "=== Step 1: aapt compile resources ==="
"$ANDROID_HOME/build-tools/34.0.0/aapt" package -f -m \
  -J gen -S res -M AndroidManifest.xml \
  -I "$ANDROID_HOME/platforms/android-34/android.jar"

echo ""
echo "=== Step 2: javac compile ==="
find src gen -name "*.java" > sources.txt
cat sources.txt
CLASSPATH="$ANDROID_HOME/platforms/android-34/android.jar"
for jar in bin/dexedLibs/*.jar; do
  CLASSPATH="$CLASSPATH:$jar"
  echo "Adding jar: $jar"
done
echo "CLASSPATH=$CLASSPATH"
javac -d bin/classes \
  -sourcepath src:gen \
  -bootclasspath "$ANDROID_HOME/platforms/android-34/android.jar" \
  -classpath "$CLASSPATH" \
  @sources.txt

echo ""
echo "=== Step 3: dx create DEX ==="
"$ANDROID_HOME/build-tools/34.0.0/dx" --dex \
  --output=bin/classes.dex \
  bin/classes bin/dexedLibs/*.jar

echo ""
echo "=== Step 4: aapt package APK ==="
"$ANDROID_HOME/build-tools/34.0.0/aapt" package -f \
  -M AndroidManifest.xml -S res \
  -I "$ANDROID_HOME/platforms/android-34/android.jar" \
  -F bin/Server.apk.unaligned

echo ""
echo "=== Step 5: add DEX to APK ==="
"$ANDROID_HOME/build-tools/34.0.0/aapt" add bin/Server.apk.unaligned bin/classes.dex

echo ""
echo "=== Step 6: zipalign ==="
"$ANDROID_HOME/build-tools/34.0.0/zipalign" -f 4 bin/Server.apk.unaligned bin/Server.apk

rm -f bin/Server.apk.unaligned
cp bin/Server.apk build/outputs/apk/debug/

echo ""
echo "=== BUILD COMPLETE ==="
ls -la bin/Server.apk
ls -la build/outputs/apk/debug/
