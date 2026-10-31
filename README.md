# Glass Home for Android TV

A calm, fast home screen for Android TV and Google TV: a big serif clock over
rotating nature photos, a quiet row of your apps, and nothing else. No ads, no
recommendation rows, no tracking.

It also comes with scripts that clean up a slow, ad-filled TV over adb: a
backup first, then switching off ads, tracking and apps you don't use, all of
it reversible with one command.

Based on [Glass Home for Samsung TVs](https://github.com/victaphu/glass-home-samsung-tv)
by victaphu, rebuilt as a native Android app.

## What you get

- **Four themes**: Clock (big serif clock, small icons), Glass (frosted dock),
  Postcard (the photo as a card, an app menu) and Type (words instead of
  icons). Pick one on the phone website; see [Themes](#themes) for memory use.
- **Your style**: five clock fonts, six date formats (written out or digits),
  five weather styles (words, icon, temperature only, large corner, corner
  with a 3-day forecast), 12/24-hour clock.
- **Photos**: 12 CC0 nature photos, your own from your phone, or a shared
  iCloud / Google Photos album that syncs by itself. Portrait photos are shown
  whole over a blurred copy; landscape ones are cropped around faces. Shuffle,
  or drag them into your own order.
- **Dock**: the TV's installed apps plus whatever is plugged into HDMI, named
  by HDMI-CEC ("PlayStation 5"). A green dot shows when a console is on.
- **Icons you can change** on any tile: the app's own, drawn device icons in
  three styles, or any picture you upload.
- **Weather** next to the date (Open-Meteo, no account or key).
- **Now playing**: track and cover art while Spotify or YouTube Music plays.
- **Night mode**: a warm, dim screen in the evening.
- **Colour buttons** on the remote open the tiles you assign to them.
- **A website on the TV** for your phone at `http://glasshome.local:8090`:
  scan the QR code on the TV and you're in. Upload and order photos and icons,
  pick the theme, font and city, reorder the dock. Home network only, behind
  a code shown on the TV.
- **Light**: about 50 MB of memory while another app is open.

## Themes

| Theme | Looks like | Memory on the home screen |
|---|---|---|
| Clock | big serif clock bottom left, small icons | THEME_MEMORY_CLOCK |
| Glass | clock top right, frosted dock of large icons | THEME_MEMORY_GLASS |
| Postcard | the photo as a postcard on a blurred copy, app menu on the left | THEME_MEMORY_POSTCARD |
| Type | apps as large words, no icons | THEME_MEMORY_TYPE |

Measured with `dumpsys meminfo` on a 2 GB Google TV. With another app in
front every theme drops its photos and uses about the same, around 50 MB.

## Languages

All text, on the TV and on the phone website, lives in one file:
[app/src/main/assets/i18n/en.json](app/src/main/assets/i18n/en.json). To add a
language, copy it to `<code>.json` (for example `ru.json`), translate the
values and rebuild; keys you leave out fall back to English. The language is
then offered on the phone website. Two strings Android reads itself (the app
name and the accessibility description) are in `res/values/strings.xml`; add
`res/values-<code>/strings.xml` for those.

## Status

Built and used on a Smarttech Apollo Premium 4K (Google TV, Android 14,
MediaTek, 2 GB RAM). It should work on any Android TV 8.0+ or Google TV, but
other TVs are untested. Reports and pull requests are welcome, especially
additions to [debloat/packages.tsv](debloat/packages.tsv) for your TV brand.

## Requirements

- A computer with [adb](https://developer.android.com/tools/releases/platform-tools)
  (`brew install --cask android-platform-tools` on a Mac).
- To build: JDK 17+ and the Android SDK (platform 34). Or use a release APK.
- On the TV: Developer options (Settings > System > About > tap Build 7 times)
  and Wireless debugging (Settings > System > Developer options).

## Set up

**Step-by-step guide, including what to do when something goes wrong:
[docs/INSTALL.md](docs/INSTALL.md).** The short version:

All scripts find the TV on your network by themselves. Every change is
written to `.tv/changes.log`, and `scripts/restore.sh` undoes all of it.

```sh
# 1. Pair once: Wireless debugging > Pair device with pairing code
scripts/tv-pair.sh 192.168.1.20:37000 123456

# 2. Back up the TV's current state (packages, settings, a screenshot)
scripts/backup.sh

# 3. Build and install Glass Home
scripts/deploy.sh

# 4. Make the Home button open it
scripts/set-home.sh

# 5. Optional clean-up
scripts/debloat.sh            # asks about every ad/tracking/unused app it finds
scripts/private-dns.sh        # blocks ads and trackers with AdGuard DNS
scripts/speed.sh              # snappier system animations
scripts/enable-now-playing.sh # track and cover art on the home screen

# Undo everything
scripts/restore.sh            # --dry-run to see what it would do first
```

Nothing is ever uninstalled: apps are disabled with `pm disable-user` and come
back with `pm enable`.

### Or let Claude Code do it

The whole thing started as one prompt to [Claude Code](https://claude.com/claude-code).
Open a terminal in this repository and try:

> My Android TV is slow and full of ads. Find it on my Wi-Fi, connect over ADB
> and clean it up: turn off the maker's ads, telemetry and junk apps (reversibly,
> uninstall nothing), asking me first about apps and features I might use. Then
> install Glass Home from this repo as the home screen. Back up before any
> change, check each step with screenshots from the TV and keep restore.sh up to date.

## Using it

- **OK** opens a tile. **Hold OK** for its menu: move, hide, change icon, put
  on a colour button, wallpaper, photos from your phone.
- **Back** closes a menu; on the home screen it does nothing.
- **Phone website**: hold OK on any tile > Add photos from your phone. The TV
  shows its address and a code.

### Official logos

Icons for PlayStation, Xbox, Nintendo Switch and Apple TV are drawn from
scratch. If you'd rather have the brand logos on your own TV:

```sh
python3 scripts/make-official-icons.py   # downloads logos from Wikimedia Commons (macOS)
scripts/push-icons.sh                    # debug builds; or upload the PNGs on the phone website
```

The logos are trademarks of their owners. They stay on your computer and
your TV and are never part of this repository or the APK.

## Good to know

- **Google TV** ignores a third-party Home app while its own launcher is on.
  Disabling the launcher or the setup wizard is risky (on our test TV it cut
  the network and the TV forgot its Wi-Fi), so `set-home.sh` keeps both and
  uses a small accessibility service that brings Glass Home to the front when
  the stock home opens. The scripts refuse to disable a list of packages the
  TV needs (`scripts/changelog.sh`).
- **After a reboot** Wireless debugging may switch itself off. Turn it on
  again; you don't need to pair again.
- **System updates** can switch the accessibility service off. Run `set-home.sh` again.
- **Colour buttons** work while the home screen is in front.
- **Google Photos albums** are read from the public share page, which Google
  can change at any time. iCloud shared albums use Apple's web API.
- **YouTube ads** come from the same servers as the videos, so DNS blocking
  can't remove them.

## Privacy

The app talks to: Open-Meteo for weather (once you pick a city), your shared
album's host (once you add a link), and nothing else. The phone website only
answers devices on your home network, needs the code from the TV, and uses
plain HTTP, so anyone on the same Wi-Fi could read the traffic.

## Credits and licences

- Code: MIT, see [LICENSE](LICENSE). Based on Glass Home for Samsung TVs by
  victaphu ([MIT](licenses/MIT-glass-home-samsung-tv.txt)).
- Photos: CC0, see [CREDITS.md](CREDITS.md).
- Instrument Serif: SIL Open Font License, see [licenses](licenses/OFL-InstrumentSerif.txt).
- Not affiliated with Google, Sony, Microsoft, Nintendo, Apple or any app shown.
