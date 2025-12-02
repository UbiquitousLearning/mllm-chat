# Android LLM Chatbot (v2) - Go Server Edition

**mllm Chat Bot (v2)** is an advanced Android application based on the multimodal LLM inference engine [**mllm**](https://github.com/UbiquitousLearning/mllm).

This version introduces a major architectural refactoring, moving from pure JNI to a **Kotlin UI + Local Go Server** architecture. It runs an HTTP server locally (`localhost:8080`) to handle inference requests via SSE (Server-Sent Events).

## ✨ New Features in v2

* **Architecture:** Local Go Server (`mllm_server.aar`) with HTTP/SSE communication.
* **Dual Mode:** Seamless switching between **Local** (completely offline) and **Cloud** modes.
* **Multimodal OCR:** Integrated DeepSeek-OCR support for image-to-text capabilities.
* **UI Upgrade:** New Settings Page for API keys/mode switching, Markdown rendering optimization, and NPU/CPU visual indicators.

## How to Build

### 1. Get the Code

```bash
git clone [https://github.com/UbiquitousLearning/mllm-chat.git](https://github.com/UbiquitousLearning/mllm-chat.git)
````

### 2\. Download Native Libraries (Required)

Due to GitHub file size limits, the required native libraries (`.so` files) are **not included** in the source code and must be downloaded separately.

1.  Go to the [**Releases Page**](https://www.google.com/search?q=https://github.com/UbiquitousLearning/mllm-chat/releases).
2.  Download the **`jniLibs.zip`** file from the latest release (v2.0).
3.  Extract the contents into your project directory at the following path:
    `app/src/main/jniLibs/`

> **Verification:** After extraction, ensure the folder structure looks like this:
>
>   * `app/src/main/jniLibs/arm64-v8a/libMllmRT.so`
>   * `app/src/main/jniLibs/arm64-v8a/libMllmCPUBackend.so`
>   * (and other .so files)

### 3\. Prepare Local Server Library

The project also relies on `mllm_server.aar` which contains the compiled Go server.

Ensure the library exists in the following path:
`app/libs/mllm_server.aar`

> **Note:** If this AAR file is missing, please check the Releases page as well, or build it from the Go source.

### 4\. Download mllm Models

For **Local Mode** to function, you need to download the converted `.mllm` model files and place them in the specific directory on your Android device.

**Required Path Structure:**
The application strictly reads models from the following paths. Please rename your downloaded models or folders to match exactly:

| Feature | Hardcoded Path on Device |
| :--- | :--- |
| **Chat Model** | `/sdcard/Download/model/qwen3` |
| **OCR Model** | `/sdcard/Download/model/deepseek_ocr` |

#### File Placement Example

You can use `adb push` to move files to your phone:

```bash
# Example structure for Qwen3
adb shell mkdir -p /sdcard/Download/model/qwen3
adb push ./qwen3-model-file.mllm /sdcard/Download/model/qwen3/
adb push ./qwen3_vocab.mllm /sdcard/Download/model/qwen3/

# Example structure for OCR
adb shell mkdir -p /sdcard/Download/model/deepseek_ocr
adb push ./ocr-model-file.mllm /sdcard/Download/model/deepseek_ocr/
```

### 5\. Build the App

Import the project into Android Studio (Ladybug or newer recommended) and sync Gradle.

Or build via command line:

```bash
./gradlew assembleDebug
```

## Usage Guide

1.  **Permissions:** On first launch, grant "All files access" permission so the app can load models from `/sdcard`.
2.  **Settings:** Click the gear icon in the top right corner.
    * **Local Mode:** Ensure models are loaded. The app will connect to the local Go server.
    * **Cloud Mode:** Enter your API Key. (API Keys are stored locally and never uploaded).
3.  **Chat:**
    * Type text to chat with Qwen3 (Local) or DeepSeek (Cloud).
    * Upload an image to trigger the OCR function.
4.  **Indicators:** Watch the top bar for **CPU/NPU** icons to see which hardware is accelerating your local inference.

<!-- end list -->

