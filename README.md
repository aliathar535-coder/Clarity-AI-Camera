# Clarity AI Camera

## What's included
- Android CameraX live preview and photo capture
- On-device sharpening and mild contrast enhancement
- Original photo comparison
- Save enhanced photo to `Pictures/ClarityAICamera`
- GitHub Actions workflow that builds a debug APK

## Important note
The current version uses lightweight on-device image processing, not a trained AI super-resolution/restoration model. It can make edges look crisper, but it cannot reliably recreate details missing from the original. Online AI and a true offline ML model are not yet integrated; those require a compatible model and, for online processing, a secure inference service.

## Build APK using GitHub (phone-friendly)
1. Download and extract this ZIP on your phone.
2. Create a new repository on GitHub.
3. Upload the *contents* of this extracted folder to the repository root. Do not upload an extra parent folder.
4. Open the repository's **Actions** tab.
5. Select **Build Android APK**, then tap **Run workflow** (or wait for the first push build).
6. Open the completed workflow run and find **Artifacts**.
7. Download `ClarityAICamera-debug-apk.zip`, extract it, and install `app-debug.apk`.
8. If Android blocks installation, allow installation from that browser/file manager when prompted.

This is a starter project. GitHub builds the APK; it does not mean the app has been tested on every device.
