# Screen Dimmer for Fire TV Stick

A minimal Android TV app that draws a translucent black overlay on top of
everything else on screen, with a slider (0-90%) to control how dark it is.
It runs as a foreground Service, so dimming keeps working after you press
Home and switch to Netflix/Prime/YouTube/etc.

---

## 0. Try this first (may save you the whole build)

Your original problem wasn't that dimmer apps don't work on TVs — it's that
the "Allow" overlay-permission button couldn't be pressed. On Fire TV Stick
you can grant that permission over ADB, completely bypassing the on-screen
button:

```
adb shell appops set <package_name> SYSTEM_ALERT_WINDOW allow
```

If you already have a dimmer app installed on the Fire Stick, find its
package name (`adb shell pm list packages | grep -i dim`) and try the
command above, then reopen the app. If the overlay appears, you may not
need the custom app at all. If it still doesn't work (or you just want the
custom, ad-free, minimal version), continue below — the same ADB trick is
used for this app too.

---

## 1. Build the APK — no local install needed

I generated the full source project here, but I don't have network/Android
SDK access in this sandbox to compile it into a binary. Pick whichever of
these suits you — none of them require installing Android Studio.

### Option A (recommended): GitHub Actions — builds in the cloud, browser only

This repo already includes `.github/workflows/build.yml`, which tells
GitHub to compile the APK for you for free.

1. Create a free account at github.com if you don't have one.
2. Create a new **public** repository (e.g. `ScreenDimmer`). Public repos
   get unlimited free Actions minutes.
3. On the empty repo page, click **"uploading an existing file"**.
   Unzip `ScreenDimmer.zip` on your computer first (Windows/macOS can do
   this natively, no extra software), then drag the *contents* of the
   `ScreenDimmer` folder (not the folder itself) into the browser drop
   zone. Commit.
4. Go to the **Actions** tab. A "Build APK" run should start automatically
   (or click **Run workflow** if not). It takes ~3-5 minutes.
5. Once it's green, click into the run → under **Artifacts**, download
   `app-debug` → unzip it → you now have `app-debug.apk`.
6. Optional but very useful: go to **Releases → Draft a new release**,
   attach `app-debug.apk` as a binary, publish it. This gives you a stable
   public download link — handy for the next step.

### Option B: Termux, entirely on an Android phone/tablet, no PC or cloud

1. Install [Termux](https://f-droid.org/packages/com.termux/) (get it from
   F-Droid, not the outdated Play Store version).
2. In Termux:
   ```
   pkg install openjdk-17 gradle git
   git clone <your-repo-url>   # or transfer the folder to the phone another way
   cd ScreenDimmer
   ```
3. You still need Android SDK command-line tools + build-tools + platform
   on-device (this is the fiddly part — search "build APK in Termux
   android SDK" for an up-to-date guide, since exact package names shift).
4. Once set up: `gradle assembleDebug`.
   This works but is more manual than Option A — only worth it if you
   specifically don't want to touch any cloud service.

### Option C: Ask someone with Android Studio / the SDK already installed

Send them the `ScreenDimmer.zip`, they run `Build APK` once, send the
`.apk` back. Zero setup on your end.

---

## 2. Get the APK onto the Fire TV Stick

### Option 1: No PC involved at all — "Downloader" app (recommended if you used GitHub Releases above)

1. On the Fire Stick, install the free **Downloader** app from the Amazon
   Appstore (search for "Downloader", by AFTVnews — it's extremely common
   for this exact purpose).
2. Also turn on **Apps from Unknown Sources**: Settings → My Fire TV →
   Developer Options (if you don't see this menu, go to Settings → My Fire
   TV → About and click the device name 7 times to unlock it) → enable
   **Apps from Unknown Sources**.
3. Open Downloader, enter the GitHub Release URL for `app-debug.apk` from
   step 6 above, let it download, then choose Install when prompted.
4. Grant the overlay permission normally through the on-screen prompt, or
   via **Settings → Applications → Manage Installed Applications → Screen
   Dimmer → Permissions**. Since Fire OS's own settings menus aren't
   locked down the way your other TV's firmware was, this button should be
   pressable here — try it before falling back to ADB.

### Option 2: Over ADB, if the Settings "Allow" button still won't press

You'll need `adb` (the small ~10MB platform-tools package — not Android
Studio) on your PC:
- Download **just the platform-tools** zip from
  https://developer.android.com/tools/releases/platform-tools , unzip it —
  no installer, no Studio.
- On the Fire Stick, enable **ADB debugging** in the same Developer
  Options menu as above, and note its IP: Settings → My Fire TV → About →
  Network.

```
adb connect <firestick_ip>:5555
adb install app-debug.apk
adb shell appops set com.example.screendimmer SYSTEM_ALERT_WINDOW allow
```

## 3. Launch it

It should appear on the Fire TV home screen under "Apps". If it doesn't
show up in the launcher grid (sideloaded apps without a proper banner
image sometimes don't), use:
**Settings → Applications → Manage Installed Applications → Screen Dimmer
→ Launch application**, or over ADB:
```
adb shell monkey -p com.example.screendimmer -c android.intent.category.LAUNCHER 1
```

---

## Using it

- Move the slider with the remote's Left/Right while it's focused — 0 to 90%.
- Press **Start Dimming** — the overlay applies immediately, and a
  persistent notification confirms it's running.
- Press **Home** on the remote — the overlay stays active while you use any
  other app.
- Reopen the app any time to adjust the level or press **Stop Dimming** to
  turn it off. Your last chosen level is remembered.

## Notes / limitations

- The overlay is capped at 90% opacity on purpose — 100% would just black
  out the screen entirely with no way to see what you're doing.
- It's a visual-only overlay (`FLAG_NOT_TOUCHABLE` / `FLAG_NOT_FOCUSABLE`),
  so it never intercepts remote button presses — whatever app is underneath
  still gets full control.
- If Fire OS kills the app under heavy memory pressure, the overlay will
  disappear; `START_STICKY` asks the system to restart the service, but
  this isn't 100% guaranteed on every Fire OS version. If you notice this
  happening often, let me know and I can add a "restart on boot" receiver.
- No launcher banner image is included (keeps the project dependency-free);
  if you want a nicer tile on the home screen, drop a 320×180 PNG at
  `app/src/main/res/drawable/banner.png` and add
  `android:banner="@drawable/banner"` to the `<application>` tag in
  `AndroidManifest.xml`.
