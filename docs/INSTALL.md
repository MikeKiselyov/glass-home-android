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
   Releases page and run the set-up with `--apk glass-home.apk` in step 4.

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

## 4. Run the set-up

```sh
scripts/setup.sh
```

It goes through these steps and asks before each one; Enter takes the
recommended answer in [brackets]. `scripts/setup.sh --yes` takes all the
recommended answers without asking, and every answer can be given as an
option instead (`scripts/setup.sh --help` lists them, the README has a table).

1. **Backup.** The TV's state goes to `.tv/backup/<date-time>/`: every app and
   whether it is on, all system settings, the Home app, a screenshot and a
   copy of the change log. It changes nothing on the TV. A new backup is made
   every time; old ones are kept.
2. **Install.** Builds the release APK and installs it, then compiles it
   ahead of time on the TV so it starts quickly from the first day. With a
   downloaded APK: `scripts/setup.sh --apk glass-home.apk`.
3. **Home button** (recommended: on). Google TV and most Android TVs keep
   their own launcher on Home whichever app you choose. Glass Home turns on a
   small accessibility service that catches the Home key and opens Glass Home,
   so the stock home screen doesn't start or flash. Holding Home no longer
   opens the Google TV panel. On other TVs Glass Home is simply made the
   default Home app. The stock launcher and the setup wizard are never
   disabled: disabling the setup wizard is what made our test TV drop off the
   network and forget its Wi-Fi, so the scripts refuse to touch it.
4. **Now playing** (on): lets Glass Home show the track and cover art while
   Spotify or YouTube Music plays.
5. **Clean-up** (junk): switches off ads, tracking and demo apps found on this
   TV ([debloat/packages.tsv](../debloat/packages.tsv)). Answer `ask` to go one
   by one; "features" you might use (voice search, Chromecast, Google's
   screensaver) are only switched off when you say so.
6. **Animations** (0.5): system animations twice as fast.
7. **Ad-blocking DNS** (keep): AdGuard's Private DNS for every app. YouTube
   ads come from the same servers as the videos, so DNS can't remove those.
8. **Screensaver** (keep): `colors` or `off` stops Google's photo
   screensaver from using the network and memory while the TV is idle.

Open Glass Home once to check it looks right: press Home.

### Or step by step

Each step is its own script, if you'd rather run them one at a time:

```sh
scripts/backup.sh              # 1
scripts/deploy.sh --release    # 2 (without --release: a debug build, for development)
scripts/set-home.sh            # 3
scripts/enable-now-playing.sh  # 4
scripts/debloat.sh             # 5
scripts/speed.sh               # 6
scripts/private-dns.sh         # 7
```

Some settings can't be changed safely over adb. Do these by hand if you like:

- Settings > Privacy > **Usage & diagnostics**: off.
- Settings > Privacy > **Ads**: turn off personalisation, or delete the
  advertising ID.

## 5. Set up from your phone or on the TV

On the TV, open **Home settings**, the last tile on the dock, to change
everything with the remote. Or hold **OK** on any tile and choose **Set up from
your phone**.
Scan the QR code with your phone's camera (or open `glasshome.local:8090` and
enter the code), and you can:

- upload photos and icons, and drag photos into your own order,
- choose the theme, clock font and date format,
- link an iCloud or Google Photos shared album,
- pick the city for the weather,
- reorder or hide dock tiles,
- set the photo interval, clock format and night mode.

## Compare before and after (optional)

To see what changed on your own TV, measure it before the set-up and after:

```sh
scripts/compare.sh before    # before step 4
scripts/compare.sh after     # after it; prints one table
```

Each takes about 7 minutes (3 runs) and changes nothing on the TV. For a
fair result reboot the TV before each, or don't reboot at all.

## Undo

```sh
scripts/restore.sh --dry-run   # shows what it would do
scripts/restore.sh             # undoes every change, newest first
scripts/restore.sh --uninstall # ...and removes Glass Home too
```

`restore.sh` makes a backup of the state before it starts, then reads
`.tv/changes.log` from the newest change back: switched-off apps are switched
on, each setting gets back the value it had, the Home button goes back to the
stock launcher. The log is then kept as `.tv/changes.log.restored-<date-time>`.

If something looks wrong afterwards, compare the TV with a backup: each
folder in `.tv/backup/` has `packages-disabled.txt`, `settings-*.txt` and
`home-activity.txt` as plain text.

## When something goes wrong

**The scripts can't find the TV.** Wireless debugging often switches itself
off after a reboot. Turn it on again (Developer options > Wireless debugging).
You don't need to pair again.

**The TV lost its Wi-Fi.** Settings > Network & Internet > pick your network
again, then turn Wireless debugging back on. If you disabled anything by hand
outside these scripts, run `scripts/restore.sh` once you're connected.

**Home shows the stock screen.** Check Settings > Accessibility > Glass Home is
on, or run `scripts/set-home.sh` again. A system update can switch it off, and so
does **Force stop** on Glass Home in Settings > Apps (Android does that to every
app with an accessibility service).

**Home still shows Google TV for a moment.** Some TVs handle the Home key
before accessibility services see it. Glass Home then still comes in front as
soon as Google TV opens. On the website, More > Home button > After Google TV
makes that the only way.

**Fixing it without a computer.** Settings > Apps > See all apps > Show
system apps: anything disabled can be enabled again there. Settings >
Accessibility > Glass Home: off brings back the stock Home.

**An app is missing from the dock.** Hold OK on any tile > Show hidden apps,
or check the phone website's Dock section.
