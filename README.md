# Fluency Coach (Open-Speech)

An AI-driven English speaking coach app built with native Jetpack Compose and powered by Gemini AI.

**Live website:** https://sunnydev07.github.io/Open-Speech/

**Download the app:** https://sunnydev07.github.io/Open-Speech/app-debug.apk
(Android APK, ~23 MB — enable "Install unknown apps" when installing.)

## What the site offers

- Interactive phone simulator: Dashboard, Live Recording timer, AI Analyzing, Fluency Results with pronunciation and accent feedback.
- Native Pixel 8 Compose screenshot gallery rendered from the Kotlin source.
- Direct APK download button in the site header.

## Run the app locally (Android)

Requirements: Android Studio (Koala or newer), JDK 17.

```bash
git clone https://github.com/sunnydev07/Open-Speech.git
```

Then open the folder in Android Studio and Run on an emulator or device. See `.env.example` for the `GEMINI_API_KEY` setup.

Or install the prebuilt APK:

```bash
adb install preview/app-debug.apk
```

## Preview the website locally

```bash
cd preview
py -m http.server 8001
# open http://localhost:8001/index.html
```

## How hosting works

- Site source lives in `preview/` (`index.html` + `screenshots/` + `app-debug.apk`).
- `.github/workflows/pages.yml` deploys the `preview/` folder to GitHub Pages on every push to `main`.
- First-time setup (one time, in the GitHub web UI): Settings → Pages → Build and deployment → Source: **GitHub Actions**. Then run the workflow or push to `main`.
- Repo homepage (About → website) is set to https://sunnydev07.github.io/Open-Speech/ so users can reach the site and download the app from there.
