# Open WebUI Client for Android

A minimal, open-source Android wrapper for a self-hosted [Open WebUI](https://github.com/open-webui/open-webui) instance, providing a native-app feel for interacting with your AI assistant from an Android device.

> Based on [Maticcm/Open-WebUI-Client-for-Android](https://github.com/Maticcm/Open-WebUI-Client-for-Android) by Matic Čuk Mikeln, licensed under the GNU GPL v3.

## About

The app is a WebView wrapper around the Open WebUI web application. It ships no AI models or backend logic; you must host your own Open WebUI server.

## Features

- Lightweight and minimal
- Dark mode (follows system theme)
- Local storage of the server URL
- Login and session persistence
- Full access to Open WebUI features through the mobile interface

## Installation

Download the latest APK from the [Releases](https://github.com/LouisPfirmann/Open-WebUI-Android-Client/releases) page.

> Make sure an Open WebUI instance is already running and reachable from your Android device.

## Getting Started

1. Launch the app
2. Enter your Open WebUI instance URL (e.g. `http://192.168.1.50:3000`)
3. Log in (if authentication is enabled)
4. Start chatting

## FAQ

### How do I change the URL?

Hold the screen with four fingers for three seconds to open the URL change menu.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
