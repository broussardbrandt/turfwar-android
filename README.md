# Turf War — Android release build

This turns the Jagneaux-Mow web app into an installable Android app and produces the
signed `.aab` file that Google Play's **Create new release** page asks for.

- Package name: `com.jagneaux.mow` (permanent, already set)
- App name: Turf War (change any time in Play Console and in `android/app/src/main/res/values/strings.xml`)
- Version: 1.0, version code 1

You do not need Android Studio. GitHub builds it for you.

---

## 1. Put this folder in a GitHub repo

Make a new repo, for example `turfwar-android`, and upload everything here **except**
`upload-keystore.jks` and `keystore-base64.txt`. Those two are your signing key and must stay
out of the repo. Keep a copy somewhere safe first — see step 2.

## 2. Save your signing key

`upload-keystore.jks` is the key that proves builds came from you. Google accepts uploads
only when they're signed with it.

1. Back it up somewhere you won't lose: Google Drive, a password manager, a thumb drive.
2. In your repo, go to **Settings → Secrets and variables → Actions → New repository secret**
   and add these four:

   | Name | Value |
   |---|---|
   | `KEYSTORE_B64` | the entire contents of `keystore-base64.txt` (one long line) |
   | `KEYSTORE_PASSWORD` | `turfwar2026` |
   | `KEY_ALIAS` | `turfwar` |
   | `KEY_PASSWORD` | `turfwar2026` |

3. Change those passwords later if you like: run
   `keytool -storepasswd -keystore upload-keystore.jks` and update the secrets to match.

If this key is ever lost or leaked, it's recoverable. Play App Signing lets you request an
upload key reset from Google, so you don't lose the app.

## 3. Build it

Open the **Actions** tab, pick **Build Turf War release bundle**, and tap **Run workflow**.
About five minutes later, under **Releases**, you'll find:

- `TurfWar.aab` — the file you upload to Play Console
- `TurfWar.apk` — a file you can install directly on your phone for testing

## 4. Create the release in Play Console

1. Play Console → your app → **Testing → Internal testing** (start here, not Production).
2. **Create new release**.
3. Upload `TurfWar.aab`.
4. When it asks about signing, accept **Play App Signing**.
5. **Release name**: type `turfwar`.
6. **Release notes**: whatever you want, e.g. "First build: cut tracking, yards, machines, league."
7. **Next → Save → Review release → Start rollout to Internal testing**.
8. Under **Testers**, add email addresses and copy the opt-in link to share.

Publishing to Production also requires, in **Policy → App content**: a privacy policy URL,
the Data safety form (declare location and email), an ads declaration, and a content rating
questionnaire. If your developer account is personal rather than a company, Google also
requires 12 testers opted in for 14 continuous days before Production opens up.

## 5. Later updates

Edit `www/index.html` (that's the whole app), then in `android/app/build.gradle` bump:

```
versionCode 2
versionName "1.1"
```

Play rejects an upload whose `versionCode` isn't higher than the last one. Commit, run the
workflow, upload the new `.aab`, and name the release `turfwar-2`.

---

## What this build does and doesn't do

The app is the web app running in a native shell, so every feature is there: tracking,
yards, machines, maintenance, the league, comments, and accounts. Two Android-specific notes:

- **The screen stays on while the app is open**, so the GPS track doesn't pause mid-cut.
- **Tracking still stops if you leave the app or lock the phone.** Real background tracking
  needs a foreground location service, which is a separate piece of work. When that's added,
  Play will also require a short video showing why the app needs background location.
