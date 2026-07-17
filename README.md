# Mobby

Mobby is an Android accessibility assistant that operates across the apps already installed on the phone. The user opens Mobby once to grant the requested permissions, then opens a small Mobby voice panel over the app they are using.

## What it can do

- Read the visible text and labelled controls on the current screen: “What’s on screen?”
- Give an on-device screen guide: “Guide me” or “How do I use this app?”
- List and activate a visible control: “What can I tap?” or “Tap Search”
- Enter text into the focused text box: “Type hello” or “Reply that I’m running late”
- Scroll, go back, or return Home
- Send a current message only after a separate “Send” command and confirmation
- Check or reply to a person through active Android message notifications: “Reply to Maya that I’ll call later”

The named-person notification flow works with any messaging app that exposes Android’s **Direct Reply** action. For other platforms, the user opens the conversation, focuses the message field, then asks Mobby to type the reply and later confirms “Send.”

## Consent and privacy

Mobby presents three separate setup requests:

1. **Microphone** for a user-initiated voice command. There is no always-on hotword listener.
2. **Device controls** in Android Accessibility Settings. This gives Mobby access to visible text and controls, plus the ability to tap, type, scroll, and navigate in response to a user command.
3. **Message access** in Android Notification access Settings, optionally, for notification-based message checks and Direct Reply.

Screen text is processed in memory on the device and is not stored or uploaded. Mobby asks for a second confirmation before every tap, text entry, or send action. Android does not allow an app to grant itself Accessibility or notification access; the device owner must explicitly enable both in system Settings.

## Use it on another app

1. Complete the first two setup cards in Mobby.
2. Tap **Open Mobby controls over other apps**. Mobby goes to the background and leaves a small panel at the bottom of the screen.
3. In any app, tap **Speak** and give a command. The Android Accessibility button can also open or close the panel.

## Build and install

Open this folder in Android Studio with JDK 17 and Android SDK Platform 35. Let Android Studio download the Android Gradle Plugin, then choose **Build → Build APK(s)**. The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Copy that APK to the phone and open it. Android will ask the user to allow installation from that source.

## Platform limits

- Mobby can read standard Android accessibility text and controls. Canvas-only interfaces, protected screens, and unlabelled images may not expose enough information to describe or control.
- The built-in “Guide me” feature describes the current screen and its available controls. Rich, step-by-step help for an unfamiliar app would need an additional AI model provider; none is wired in, so Mobby never sends screen data to a third party.
- Apps can prevent accessibility-based editing or omit Direct Reply. In those cases Mobby explains the limitation instead of bypassing the app’s protection.
