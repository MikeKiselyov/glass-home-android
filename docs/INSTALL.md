# Install guide

This takes about 30 minutes the first time. You need a Mac, Linux or Windows
computer on the same Wi-Fi as the TV. Nothing here is permanent: every change
is logged and `scripts/restore.sh` puts the TV back the way it was.

Commands are for macOS and Linux. On Windows, run them in WSL or Git Bash.

## 1. Get the tools

1. Install **adb** (Android platform tools):
   - macOS: `brew install --cask android-platform-tools`
   - Linux: `sudo apt install adb` (or your distribution's package)
   - Windows: [platform-tools](https://developer.android.com/tools/releases/platform-tools), then add the folder to PATH
2. Download this repository (Code > Download ZIP, or `git clone`) and open a
   terminal in its folder.
3. To build the app yourself you also need **JDK 17+** and the **Android SDK**
   (platform 34). If you'd rather not, download `glass-home.apk` from the
   Releases page and skip the build in step 5.

## 2. Turn on Developer options on the TV

1. Settings > System > About.
2. Select **Android TV OS build** (or **Build**) seven times, until it says
   you are a developer.
3. Back to Settings > System > **Developer options**.
4. Turn on **Wireless debugging** (Android 11+). Older TVs: turn on
   **USB debugging** / **Network debugging** instead.

## 3. Connect the computer to the TV

1. On the TV: Developer options > Wireless debugging > **Pair device with
   pairing code**. It shows a code and an address like `192.168.1.20:37123`.
2. On the computer:

   ```sh
   scripts/tv-pair.sh 192.168.1.20:37123 123456
   ```

3. The TV may ask to allow debugging from this computer: choose
   **Always allow**.

You pair once. Later, every script finds the TV by itself, even after its
address or port changes. Older TVs without pairing: `adb connect <tv-address>:5555`.

## 4. Back up

```sh
scripts/backup.sh
```

This saves the list of apps and their state, the system settings, the current
Home app and a screenshot to `.tv/backup/`. It changes nothing on the TV.

## 5. Install Glass Home

```sh
scripts/deploy.sh                      # builds and installs
# or, with a downloaded APK:
adb install glass-home.apk
```

Open it once from the TV's app list to check it looks right.

## 6. Make Home open Glass Home

```sh
scripts/set-home.sh
```

- **Google TV and most Android TVs**: the stock launcher stays installed and
  enabled. Glass Home turns on a small accessibility service that brings it
  to the front as soon as the stock home screen opens. You may see the stock
  screen for a split second.
- **Other TVs**: Glass Home is set as the default Home app.

The script never disables the stock launcher or the setup wizard. Disabling
the setup wizard is what made our test TV drop off the network and forget its
Wi-Fi, so it is on a list of packages the scripts refuse to touch.

## 7. Clean up (optional)

```sh
scripts/debloat.sh             # asks about every ad, tracking or unused app found on this TV
scripts/private-dns.sh         # ad and tracker blocking for all apps (AdGuard DNS)
scripts/speed.sh               # quicker system animations
scripts/enable-now-playing.sh  # show the playing track on the home screen
```

`debloat.sh` disables, never uninstalls. "Junk" (ads, tracking, demo apps)
defaults to yes; "features" you might use (voice search, Chromecast, the Google
screensaver) default to no.

Some settings can't be changed safely over adb. Do these by hand if you like:

- Settings > Privacy > **Usage & diagnostics**: off.
- Settings > Privacy > **Ads**: turn off personalisation, or delete the
  advertising ID.

## 8. Set up from your phone

On the TV, hold **OK** on any tile and choose **Set up from your phone**.
Scan the QR code with your phone's camera (or open `glasshome.local:8090` and
enter the code), and you can:

- upload photos and icons, and drag photos into your own order,
- choose the theme, clock font and date format,
- link an iCloud or Google Photos shared album,
- pick the city for the weather,
- reorder or hide dock tiles,
- set the photo interval, clock format and night mode.

## Undo

```sh
scripts/restore.sh --dry-run   # shows what it would do
scripts/restore.sh             # undoes every change, newest first
scripts/restore.sh --uninstall # ...and removes Glass Home too
```

## When something goes wrong

**The scripts can't find the TV.** Wireless debugging often switches itself
off after a reboot. Turn it on again (Developer options > Wireless debugging).
You don't need to pair again.

**The TV lost its Wi-Fi.** Settings > Network & Internet > pick your network
again, then turn Wireless debugging back on. If you disabled anything by hand
outside these scripts, run `scripts/restore.sh` once you're connected.

**Home shows the stock screen.** Check Settings > Accessibility > Glass Home is
on, or run `scripts/set-home.sh` again. System updates can switch it off.

**Fixing it without a computer.** Settings > Apps > See all apps > Show
system apps: anything disabled can be enabled again there. Settings >
Accessibility > Glass Home: off brings back the stock Home.

**An app is missing from the dock.** Hold OK on any tile > Show hidden apps,
or check the phone website's Dock section.
