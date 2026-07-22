# Mobby — Voice-Controlled Android Accessibility & AI Assistant

**Mobby** is a privacy-first, voice-controlled Android assistant that operates seamlessly as an overlay across any installed app on your phone. Designed for accessibility and hands-free interaction, Mobby lets users control their device through natural voice commands, inspect on-screen controls, perform multi-step app automations, and compose replies—all while ensuring user consent and privacy.

---

## 🌟 Key Features

### 🧠 Dual-Engine Intelligence
- **Local Hybrid NLP Intent Engine (`LocalIntentClassifier.kt` & `CommandParser.kt`)**: Zero-latency, 100% offline natural language classifier combining TF-IDF token vectorization, cosine similarity matching, regex parsing, and Levenshtein fuzzy edit-distance string matching for instant execution of standard voice commands.
- **Cloud AI Brain (`GeminiBrain.kt`)**: Powered by Google’s **Gemini 2.5 Flash** model (`gemini-2.5-flash`). When a request requires open-ended reasoning, multi-step navigation, or text generation (e.g., composing emails or multi-step app workflows), Mobby converts screen layout trees into structured context and streams actions back to the device safely.

### 🎯 Smart Device Control & Self-Correction
- **3-Tier Actionable Tap Fallbacks (`DeviceController.kt`)**: Tapping a control employs a resilient fallback hierarchy:
  1. Direct Accessibility Node Click (`AccessibilityNodeInfo.ACTION_CLICK`)
  2. Coordinate-based Gesture Tap (`service.dispatchGesture`)
  3. Parent Container Bounds Tap
  4. Fuzzy Label Node Search & Coordinate Tap
- **Self-Correction Scroll Loops**: If a requested control is not visible, Mobby automatically executes scroll-down and scroll-up attempts to bring the element into view before reporting an error.

### 💬 Messaging & Notification Integration
- **Direct Reply via Notifications (`MobbyNotificationListener.kt`)**: In-memory caching of active messaging notifications allows Mobby to read recent messages and reply directly via Android's native `RemoteInput` action without opening the app or sending message text to any cloud service.
- **In-App Messaging**: When inside a messaging app, Mobby can type text into active message fields and trigger send actions upon user confirmation.

### ⏰ Background Scheduled Automations
- **WorkManager Integration (`TaskScheduler.kt` & `MobbyWorker.kt`)**: Schedule recurring automated tasks (e.g., checking messages every 30 minutes, or running a routine daily at 08:30). Tasks run reliably in the background using Android `WorkManager` and automatically summon Mobby when triggered.

### 🎙️ Opt-In Wakeword Detection
- **Continuous "Hey Mobby" Trigger (`WakewordDetector.kt`)**: Users can enable background wakeword detection. To protect battery life, Mobby uses an exponential backoff retry mechanism (`RESTART_BACKOFF_MS = 1500ms`) and displays an ongoing system notification for total transparency.

### 🔘 Volume Key Hardware Shortcut
- **System-Wide Accessibility Shortcut**: Users can instantly summon or dismiss the Mobby overlay from any app by pressing and holding both Volume keys for 3 seconds.

### 🛡️ Configurable Safety & Confirmation System
- **Confirmation Protection**: Configurable setting allowing instant auto-execution for routine steps while strictly enforcing confirmation dialogs for critical/destructive actions (e.g., "Send", "Delete", "Confirm", "Submit", "Discard").
- **Voice & Touch Confirmations**: Confirmation cards support both tap interactions and hands-free voice responses ("Yes", "Confirm", "Cancel").

### 🎨 Modern Material 3 UI & Floating Glassmorphism Overlay
- **Jetpack Compose Dashboard (`MainActivity.kt`)**: Sleek setup screen providing intuitive permission management cards, Gemini API key configuration, automation management, and preference toggles.
- **Draggable Overlay Bubble (`OverlayManager.kt`)**: Minimalist glassmorphism floating action button anchored to `TYPE_ACCESSIBILITY_OVERLAY` with integrated Text-to-Speech (`TextToSpeech`) feedback.

---

## 🏗️ Architecture & Core Components

```
+-----------------------------------------------------------------------------------+
|                                  MainActivity                                     |
|                       (Jetpack Compose Setup & Settings)                          |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                             MobbyAccessibilityService                             |
|                        (System Accessibility Service Entry)                       |
+-----------------------------------------------------------------------------------+
       |                  |                   |                   |          |
       v                  v                   v                   v          v
+--------------+  +----------------+  +---------------+  +-------------+ +--------------+
| Voice        |  | Command        |  | ScreenReader  |  | Device      | | Overlay      |
| Controller   |  | Dispatcher     |  | (Tree Parsing |  | Controller  | | Manager      |
| (SpeechRec)  |  | (Router)       |  |  & Formatting)|  | (Taps/Types)| | (Bubble/TTS)|
+--------------+  +----------------+  +---------------+  +-------------+ +--------------+
                          |                   |
               +----------+----------+        |
               |                     |        |
               v                     v        v
    +--------------------+  +-----------------------+
    | LocalIntent        |  | GeminiBrain           |
    | Classifier         |  | (Cloud AI Agent Engine|
    | (TF-IDF NLP Engine)|  |  Gemini 2.5 Flash)    |
    +--------------------+  +-----------------------+
```

### Module Index

| Component | File | Responsibilities |
| :--- | :--- | :--- |
| **Service Entry** | [`MobbyAccessibilityService.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/MobbyAccessibilityService.kt) | Central orchestrator connecting accessibility tree, speech recognition, overlay UI, and dispatchers. |
| **Local NLP Engine** | [`LocalIntentClassifier.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/LocalIntentClassifier.kt) | Offline TF-IDF vectorizer & cosine similarity intent matcher against exemplar training phrases. |
| **Command Parser** | [`CommandParser.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/CommandParser.kt) | Regex & string grammar rule parser with fallback to local intent classifier. |
| **Command Dispatcher** | [`CommandDispatcher.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/CommandDispatcher.kt) | Core execution engine routing commands, managing Gemini multi-step sessions, and handling confirmations. |
| **Cloud AI Agent** | [`GeminiBrain.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/GeminiBrain.kt) | Connects to Google Gemini API (`gemini-2.5-flash`), constructs prompt payloads, and parses structured action responses. |
| **Screen Reader** | [`ScreenReader.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/ScreenReader.kt) | Traverses Android accessibility node hierarchy, builds XML layout descriptions, and computes Levenshtein fuzzy string matches. |
| **Device Controller** | [`DeviceController.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/DeviceController.kt) | Performs physical taps, text entry, window scrolling, back/home navigation, and self-correction retry loops. |
| **Overlay Manager** | [`OverlayManager.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/OverlayManager.kt) | Manages draggable overlay bubble, visual confirmation dialogs, and Text-To-Speech lifecycle. |
| **Voice Controller** | [`VoiceController.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/VoiceController.kt) | Wraps Android `SpeechRecognizer`, configures silence timeouts, and processes audio transcripts. |
| **Wakeword Detector** | [`WakewordDetector.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/WakewordDetector.kt) | Continuous background listener for "Mobby" trigger word with backoff protection and foreground notification. |
| **Notification Access** | [`MobbyNotificationListener.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/MobbyNotificationListener.kt) | Listens for active messaging notifications and delivers replies via `RemoteInput`. |
| **Task Scheduler** | [`TaskScheduler.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/TaskScheduler.kt) | Persists background automations and schedules recurring work with WorkManager. |
| **Background Worker** | [`MobbyWorker.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/MobbyWorker.kt) | `CoroutineWorker` triggered by WorkManager to execute scheduled AI tasks or post notifications. |
| **UI Dashboard** | [`MainActivity.kt`](file:///Users/yashchaurasia/Documents/Codes/Mobby%20_c/app/src/main/java/com/mobby/assistant/MainActivity.kt) | Jetpack Compose setup interface for permissions, API keys, automations, and settings. |

---

## 🔒 Security, Privacy & User Consent

Mobby is built around strict privacy and user authorization boundaries:

1. **Explicit Permission Setup**:
   - **Microphone (`RECORD_AUDIO`)**: Activated only when the user taps Speak or enables Wakeword detection. No audio is recorded or stored.
   - **Device Controls (`AccessibilityService`)**: Must be manually enabled by the user in system settings. Grants Mobby screen-reading and gesture capabilities on-device.
   - **Message Access (`NotificationListenerService`)**: Optional permission granting access to active message notifications for Direct Reply features.
2. **On-Device First**:
   - Standard screen parsing, local intent matching, TTS feedback, and notification reply actions run 100% locally on the device.
   - Screen contents are only sent to Google Gemini when an API key is provided and the user initiates a complex or unrecognised command.
3. **Confirmation Safeguards**:
   - Destructive or outgoing actions (sending messages, deleting data, submitting forms) explicitly prompt the user for confirmation via visual card or voice command.

---

## 🚀 Getting Started

### Installation & Setup

1. **Install APK**: Build and install the debug APK onto your Android device.
2. **Grant Permissions**:
   - Open Mobby and tap **Allow microphone**.
   - Tap **Open Accessibility** and enable **Mobby accessibility controls** in System Settings.
   - (Optional) Tap **Open message access** to grant notification permissions for Direct Reply.
3. **Configure Gemini (Optional)**:
   - Paste your Gemini API key into the **Gemini Brain** section to unlock open-ended AI capabilities.
4. **Configure Quick Shortcut**:
   - Go to Android Settings ➔ Accessibility ➔ Volume key shortcut ➔ Select Mobby to enable dual volume key summoning.
5. **Start Mobby**:
   - Tap **Start Mobby Voice Assistant**. The setup window will move to the background, leaving a floating Mobby bubble on your screen.

---

## 🛠️ Build & Development

### Prerequisites
- **JDK 17**
- **Android SDK 35** (Compile SDK 35, Target SDK 35, Min SDK 26)
- **Gradle 8.x**

### Command Line Building & Testing

To compile the application debug APK:
```bash
export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || echo "/Applications/Android Studio.app/Contents/jbr/Contents/Home")
./gradlew assembleDebug
```
The output APK is generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To run the unit test suite:
```bash
export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || echo "/Applications/Android Studio.app/Contents/jbr/Contents/Home")
./gradlew test
```

---

## 📋 Command Usage Examples

| Intent / Goal | Voice Command Example | Action Executed |
| :--- | :--- | :--- |
| **Screen Description** | *"What's on screen?"* | Reads visible text and controls aloud via TTS. |
| **App Guidance** | *"Guide me"* | Explains the app layout and suggests starting controls. |
| **List Controls** | *"What can I tap?"* | Lists all labelled interactive elements on screen. |
| **Tap Element** | *"Tap Search"* | Taps the "Search" button using 3-tier fallback logic. |
| **Text Entry** | *"Type Hello World"* | Inputs "Hello World" into the active text field. |
| **Scrolling** | *"Scroll down"* / *"Scroll up"* | Scrolls the active window in the requested direction. |
| **Navigation** | *"Go back"* / *"Go home"* | Triggers system back or home navigation actions. |
| **Check Messages** | *"Check messages from Maya"* | Searches active notifications for recent messages from Maya. |
| **Direct Reply** | *"Reply to Maya that I'll call later"* | Delivers reply via notification Direct Reply action. |
| **In-App Reply** | *"Reply that I'm running late"* | Types response into currently focused text box. |
| **Send Message** | *"Send"* / *"Tap send"* | Taps visible send control after confirmation. |
| **AI Task Execution** | *"Write a quick email thanking John"* | Invokes Gemini 2.5 Flash to plan and execute multi-step email drafting. |
