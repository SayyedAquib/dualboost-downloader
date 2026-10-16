# DualBoost Downloader for Google Pixel 9

An Android application built with **Kotlin** and **Jetpack Compose** that downloads files by splitting them into chunks and pulling them **simultaneously across Wi-Fi and Mobile Data (5G/LTE)**.

---

## ⚡ How It Works (Under the Hood)

Standard Android apps route all network requests through the default active interface (which is Wi-Fi whenever connected). 

DualBoost Downloader bypasses this restriction without requiring root:
1. **Network Requests**: Uses `ConnectivityManager.requestNetwork()` with `TRANSPORT_WIFI` and `TRANSPORT_CELLULAR` to keep both radios active and obtain independent `android.net.Network` handles.
2. **Socket Factory Binding**: Creates two distinct `OkHttpClient` instances whose sockets are bound directly to their respective physical interfaces via `network.socketFactory`.
3. **Adaptive Chunk Work Queue**: Probes the remote server for `Accept-Ranges: bytes` and `Content-Length`. Splits the file into small 4 MB byte-range segments (`Range: bytes=X-Y`).
4. **Self-Balancing Parallel Workers**: Launches concurrent coroutines on both Wi-Fi and 5G. Whichever interface finishes a chunk faster immediately claims the next pending chunk, naturally self-balancing regardless of connection speeds.
5. **Zero-Merge Disk Writes**: Uses `RandomAccessFile` to pre-allocate the file and write chunks directly to their exact byte offsets as they arrive.
6. **Background Persistence**: Runs as an Android `ForegroundService` (`dataSync`) with a live notification showing real-time Wi-Fi speed vs. 5G speed breakdown.

---

## 📱 Pixel 9 Configuration Prerequisite

By default, Android powers down the cellular radio when Wi-Fi is connected. To allow the app to bind to 5G while connected to Wi-Fi:

1. Open **Settings** on your Pixel 9.
2. Scroll to the bottom and tap **About phone**.
3. Tap **Build number** rapidly **7 times** (enter device PIN when prompted).
4. Return to **Settings > System > Developer options**.
5. Under the *Networking* section, turn **ON** **"Mobile data always active"**.

---

## 🚀 How to Build and Run (CLI & ADB - No Android Studio Needed)

### Prerequisites
* **Java 17** (e.g., OpenJDK 17)
* **Android SDK 35** with Build-Tools (configured in `local.properties`)
* **ADB** installed and available in PATH
* Pixel 9 running **Android 14 or Android 15** with USB Debugging enabled

### CLI Commands
```powershell
# 1. Verify connected device
adb devices

# 2. Build debug APK
.\gradlew.bat assembleDebug

# 3. Install APK directly onto Pixel 9
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Grant notification permission (Android 13+)
adb shell pm grant com.dualboost.downloader android.permission.POST_NOTIFICATIONS

# 5. Launch the application
adb shell am start -n com.dualboost.downloader/.ui.MainActivity

# 6. Stream live dual-network logs in real time
adb logcat -s DualNetworkManager DualDownloaderEngine
```

---

## 📚 Project Documentation

All specifications, architectural designs, and engineering rules are located in the [`docs/`](file:///A:/Development/dualboost-downloader/docs) directory:
- [Product Requirements Document (`docs/PRD.md`)](file:///A:/Development/dualboost-downloader/docs/PRD.md)
- [System Architecture (`docs/ARCHITECTURE.md`)](file:///A:/Development/dualboost-downloader/docs/ARCHITECTURE.md)
- [Design System (`docs/DESIGN.md`)](file:///A:/Development/dualboost-downloader/docs/DESIGN.md)
- [Project Rules (`docs/RULES.md`)](file:///A:/Development/dualboost-downloader/docs/RULES.md)
- [Architecture Decision Records (`docs/DECISIONS.md`)](file:///A:/Development/dualboost-downloader/docs/DECISIONS.md)
- [Testing Strategy (`docs/TESTING.md`)](file:///A:/Development/dualboost-downloader/docs/TESTING.md)
- [Project Working Memory (`docs/MEMORY.md`)](file:///A:/Development/dualboost-downloader/docs/MEMORY.md)
- [AI Agent Handbook (`AGENTS.md`)](file:///A:/Development/dualboost-downloader/AGENTS.md)


