<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/f5dbf723-0156-4769-94fc-173837c93fea

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## Email OTP backend

The Android app uses Firebase callable Cloud Functions in `us-central1`. The
registration OTP flow requires the `sendEmailOtp` and `verifyEmailOtp`
functions in the Firebase project configured by `app/google-services.json`.
The functions are in `functions/index.js`; the `firebase.json` and `.firebaserc`
files configure deployment to that same project.

Before deployment, configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`,
and an authorized `SMTP_FROM` in `functions/.env` using
`functions/.env.example` as a template. Keep real SMTP credentials only in the
backend environment; never add them to the Android app's `.env`. Then install
the Functions dependencies and deploy using the Firebase CLI:

The default Firestore database must also be created in the same Firebase
project before deployment. The OTP function stores only a salted challenge
hash there; if Firestore is missing or unavailable, the function rejects the
request before sending an email.

```sh
cd functions && npm install
cd .. && firebase deploy --only functions
```

The client reports success only after the function confirms SMTP acceptance
and stores the verification challenge. SMTP acceptance does not confirm final
inbox delivery.
