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
   scripts/tv/pair.sh 192.168.1.20:37123 123456
   ```

3. The TV may ask to allow debugging from this computer: choose
   **Always allow**.

You pair once. Later, every script finds the TV by itself, even after its
address or port changes. Older TVs without pairing: `adb connect <tv-address>:5555`.

## 4. Run the set-up

```sh
scripts/setup.sh
```

First it asks what to set up:

| Choice | Option | What you get |
|---|---|---|
| Everything (recommended) | `--only all` | Glass Home and the clean-up: steps 1 to 8 below |
| Only Glass Home | `--only glasshome` | Backup, Glass Home, Home button, now playing (steps 1 to 4). Nothing on the TV is switched off |
| Only the clean-up | `--only clean` | Backup, then ads, tracking, animations, DNS and screensaver (steps 1 and 5 to 8). Glass Home is not installed; the TV keeps its own home screen |

```sh
scripts/setup.sh --only glasshome --apk glass-home.apk   # only Glass Home, downloaded APK
scripts/setup.sh --only clean --yes                       # only the clean-up, recommended answers
```

Then it goes through the steps and asks before each one; Enter takes the
recommended answer in [brackets]. `scripts/setup.sh --yes` takes all the
recommended answers without asking, and every answer can be given as an
option instead (`scripts/setup.sh --help` lists them too):

| Step | Option | Recommended | What it does |
|---|---|---|---|
| Install | `--apk <file>` | build from source | Installs a downloaded APK instead of building (building needs JDK 17+ and the Android SDK) |
| Home button | `--home on\|off` | on | Home opens Glass Home (see [Good to know](USING.md#good-to-know)) |
| Now playing | `--now-playing on\|off` | on | Track and cover art while Spotify or YouTube Music plays |
| Clean-up | `--clean ask\|junk\|none` | junk | Switches off ads, tracking and demo apps from [debloat/packages.tsv](../debloat/packages.tsv); `ask` goes one by one, features you might use (voice search, Chromecast) are only switched off when you say so |
| Animations | `--animations 0.5\|1\|keep` | 0.5 | System animations twice as fast |
| Ad-blocking DNS | `--dns adguard\|keep` | keep | AdGuard's Private DNS for every app (not YouTube ads) |
| Screensaver | `--screensaver keep\|colors\|off` | keep | Google's photo screensaver uses the network and memory while the TV is idle |


1. **Backup.** The TV's state goes to `.tv/backup/<date-time>/`: every app and
   whether it is on, all system settings, the Home app, a screenshot and a
   copy of the change log. It changes nothing on the TV. A new backup is made
   every time; old ones are kept.
2. **Install.** Builds the release APK and installs it, then compiles it
   ahead of time on the TV so it starts quickly from the first day. With a
   downloaded APK: `scripts/setup.sh --apk glass-home.apk`. It installs Glass
   Home as its own installer and lets it install apps, so it can put an update
   on later after you say so (see [Updates](#updates)).
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
scripts/backup/backup.sh              # 1
scripts/install/deploy.sh --release    # 2 (without --release: a debug build, for development)
                               #   or scripts/install/install-apk.sh glass-home.apk for a downloaded APK
scripts/install/set-home.sh            # 3
scripts/install/enable-now-playing.sh  # 4
scripts/cleanup/debloat.sh             # 5
scripts/cleanup/speed.sh               # 6
scripts/cleanup/private-dns.sh         # 7
```

The screensaver (8) has no script of its own: `scripts/setup.sh --only clean --screensaver colors`.

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
- choose the language (English, Ukrainian, Belarusian), theme, clock font and
  date format,
- link an iCloud or Google Photos shared album,
- pick the city for the weather,
- reorder or hide dock tiles,
- set the photo interval, clock format (for the whole TV, Google's screensaver too) and night mode,
- install an update, or switch update checks off.

## Updates

Once a day Glass Home asks GitHub for the latest release. When there is a newer
one, it downloads it, checks it against the SHA-256 GitHub lists for it, and
asks on the home screen:

- **Install** (the focus starts on Later, so a stray OK installs nothing):
  Glass Home closes for a few seconds and opens in the new
  version. Settings and photos stay.
- **Later**: asked again the next day. Back, or leaving the home screen while
  the question is open, counts as Later.
- **Skip this version**: not asked again until a newer version comes out.

Nothing is installed without **Install**. The phone website has the same under
Settings > Updates, with **Check now** and a switch that stops the checks
altogether.

After Install, Android puts the update on straight away when Glass Home is its
own installer and may install apps: `setup.sh`, `deploy.sh` and
`scripts/install/install-apk.sh` set that up. Installed some other way (a plain
`adb install`, a file manager), Android first asks **Allow from this source?**
once and **Update this app?** each time. To set it up afterwards, install the
APK again with `scripts/install/install-apk.sh glass-home.apk`.

GitHub's APKs are signed with the release key. A build from source is signed
with your own key (`setup.sh` without `--apk`, `deploy.sh`), so Android can't
install a release over it: Glass Home sees that and doesn't offer the update.
To get updates, install `glass-home.apk` from the Releases page instead
(uninstall the own build first; its settings and photos go with it).

## Compare before and after (optional)

To see what changed on your own TV, measure it before the set-up and after:

```sh
scripts/measure/compare.sh before    # before scripts/setup.sh
scripts/measure/compare.sh after     # after it; prints one table
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
on, or run `scripts/install/set-home.sh` again. A system update can switch it off, and so
does **Force stop** on Glass Home in Settings > Apps (Android does that to every
app with an accessibility service).

**Home still shows Google TV for a moment.** Some TVs handle the Home key
before accessibility services see it. Glass Home then still comes in front as
soon as Google TV opens. On the website, Settings > Home button > After Google TV
makes that the only way.

**Fixing it without a computer.** Settings > Apps > See all apps > Show
system apps: anything disabled can be enabled again there. Settings >
Accessibility > Glass Home: off brings back the stock Home.

**An app is missing from the dock.** Hold OK on any tile > Show hidden apps,
or the Apps tab on the phone website.
