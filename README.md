# Android-TCP-IP-Socket
This repository consists of basic introduction for Server-Client model using Android Wifi Hotspot, which uses TCP/IP model using Primary Sockets

<b>You need two Android devices to test the demo.</b><br>

## Instructions: 

<b>Server Module:</b><br>
1. Install the Server application onto device A.<br>
2. Manually create a portable WiFi Hotspot using Android Settings.<br>
3. Run the Server Application.<br><br>

<b>Client Module:</b><br>
1. Install the client application onto device B.<br>
2. Manually connect to the hotspot, created by device A.<br>
3. Use Wifi settings to connect to the hotspot.<br>
4. Launch the Client Application.<br>

</b>Note: <br>
Server application must be launched in device A before launching Client application in Device B.</b><br>

## Build Instructions

### Manual Build
1. Clone the repository: `git clone https://github.com/poiuy105/Android-TCP-IP-Socket.git`
2. Open the project in Android Studio
3. Build the project using Gradle
4. Run the Server and Client applications on your Android devices

### Automated Build
This project uses GitHub Actions for automated building. Every push to the main branch will trigger a build process that:
1. Sets up the Android build environment
2. Builds both Server and Client APKs
3. Publishes the APKs as artifacts that can be downloaded

### Downloading APKs
1. Go to the GitHub repository
2. Click on the "Actions" tab
3. Select the latest workflow run
4. Scroll down to the "Artifacts" section
5. Download the server-apk and client-apk artifacts
6. Install the APKs on your Android devices

## Project Structure
- `Server/`: Contains the TCP server implementation
- `Client/`: Contains the TCP client implementation
- `.github/workflows/`: Contains GitHub Actions workflow files for automated building

## Features
- TCP/IP socket communication between Android devices
- Server-client model using WiFi hotspot
- Automated build process with GitHub Actions
- Proper socket resource management

