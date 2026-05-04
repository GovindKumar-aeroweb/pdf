# AeroWebAI Offline Android App

This folder wraps the static site in a native Android WebView. The app intentionally does not request the Android `INTERNET` permission, and the WebView maps `https://aerowebai.in/...` requests to files bundled under `app/src/main/assets/public`.

Generated files are saved through a native bridge into the app-specific Downloads folder and the app shows the saved path in a toast.

## Build

1. Install Android Studio or Android SDK + Gradle. The first build may need internet to download the Android Gradle plugin; the APK itself does not need internet at runtime.
2. Open this `android` folder in Android Studio.
3. Build `app` as a debug APK.

CLI build, after Gradle is installed:

```sh
cd android
gradle assembleDebug
```

The generated APK will be at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Updating Web Files

After editing the website files in the project root, refresh the bundled assets:

```sh
./sync-web-assets.sh
```
