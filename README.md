<h1 align="center">Glass Home for Android TV</h1>

<p align="center">
  A calm, fast home screen for Android TV and Google TV: a big serif clock over<br>
  rotating nature photos, a quiet row of your apps, and nothing else.<br>
  No ads, no recommendation rows, no tracking.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android%20TV-8.0%2B-3ddc84" alt="Android TV 8.0 or later">
  <img src="https://img.shields.io/badge/Google%20TV-yes-4285f4" alt="Works on Google TV">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="MIT license"></a>
</p>

<p align="center">
  <img src="docs/images/theme-clock.jpg" width="820" alt="The Clock theme: a large serif clock and the date over a photo of the Alps, the weather for the week top right and a row of app icons at the bottom">
</p>

It also comes with scripts that clean up a slow, ad-filled TV over adb: a
backup first, then switching off ads, tracking and apps you don't use, all of
it reversible with one command.

Based on [Glass Home for Samsung TVs](https://github.com/victaphu/glass-home-samsung-tv)
by victaphu, rebuilt as a native Android app.

## Contents

- [What you get](#what-you-get)
- [Themes](#themes)
- [Set up from your phone](#set-up-from-your-phone) or [on the TV](#settings-on-the-tv)
- [Speed and memory](#speed-and-memory)
- [Set up](#set-up) (install on your TV)
- [Using it](#using-it)
- [Good to know](#good-to-know) and [Privacy](#privacy)

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

Pick one on the phone website; the TV changes straight away.

<table>
  <tr>
    <td width="50%"><img src="docs/images/theme-clock.jpg" alt="Clock theme: big serif clock bottom left, small icons"></td>
    <td width="50%"><img src="docs/images/theme-glass.jpg" alt="Glass theme: clock top right, a frosted dock of large icons"></td>
  </tr>
  <tr>
    <td><b>Clock</b>: big serif clock bottom left, small icons.</td>
    <td><b>Glass</b>: clock top right, frosted dock of large icons.</td>
  </tr>
  <tr>
    <td><img src="docs/images/theme-postcard.jpg" alt="Postcard theme: the photo as a tilted postcard, an app menu with icons and names on the left"></td>
    <td><img src="docs/images/theme-type.jpg" alt="Type theme: apps as large words along the bottom, no icons"></td>
  </tr>
  <tr>
    <td><b>Postcard</b>: the photo as a postcard on a blurred copy, app menu on the left.</td>
    <td><b>Type</b>: apps as large words, no icons.</td>
  </tr>
</table>

| Theme | Memory on the home screen |
|---|---|
| Clock | about 85 MB |
| Glass | about 80 MB |
| Postcard | about 120 MB |
| Type | about 80 MB |

Measured with `dumpsys meminfo` (total PSS, fresh start) on a 2 GB Google TV.
Postcard keeps a blurred copy of the photo behind the card. With another app
in front every theme drops its photos and uses about the same, around 50 MB.
More numbers: [Speed and memory](#speed-and-memory).

## Set up from your phone

Hold **OK** on any app and choose **Set up from your phone**. Scan the code
with your phone's camera: the TV's website opens and you're signed in. Or
type `glasshome.local:8090` on a phone on the same Wi-Fi and enter the code.

<p align="center">
  <img src="docs/images/phone-panel.jpg" width="820" alt="The Set up from your phone panel on the TV: a QR code, the address glasshome.local:8090 and a 6-digit code">
</p>

The website shows the TV as it is right now at the top: its photo with the
clock, date, weather and dock drawn the way you set them, so every change can
be seen before you look up. Below it, four tabs:

- **Look**: theme, clock (24 h, 12 h or the TV's setting), clock font, date,
  weather style and night mode.
- **Photos**: add photos, shuffle or your own order, how long each one stays.
- **Apps**: the dock in order, hide or show, and the colour buttons.
- **More**: the weather city, a shared iCloud or Google Photos album, icons.

It follows the phone's light or dark setting; the sun/moon button at the top
switches it, and the phone remembers the choice.

<p align="center">
  <img src="docs/images/web-look.png" width="300" alt="The phone website in light mode: the live preview of the TV with the Alps photo and 17:55, the tabs, and the Look tab with four theme cards over the same photo, Clock selected">
  &nbsp;&nbsp;
  <img src="docs/images/web-apps.png" width="300" alt="The phone website in dark mode: the live preview and the Apps tab, apps in order with up, down and hide buttons, Live TV hidden">
</p>

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

## Settings on the TV

No phone at hand? Hold **OK** on any app and choose **Glass Home settings**, or press
**Open settings on this TV** on the phone panel. The same website opens on the
TV, already signed in, with the tabs down the left. Arrows move between
controls, **OK** picks, **Back** closes it. Adding photos and icons needs a
phone (files come from there); everything else works with the remote.

<p align="center">
  <img src="docs/images/tv-settings.jpg" width="820" alt="The settings website on the TV: the live preview and the tabs on the left, the theme cards on the right with Glass outlined by the remote's yellow focus ring and Clock marked as the current theme">
</p>

## Speed and memory

Measured on the TV it was built on (Smarttech Apollo Premium 4K, Google TV,
Android 14, 4 cores, 2 GB RAM) with `scripts/measure.sh`, debug build as
`scripts/deploy.sh` installs it, medians of 5 runs, with *Don't keep
activities* off (see [Good to know](#good-to-know)).

| Theme | Cold start | Home | Idle CPU | Memory on screen | Behind another app |
|---|---|---|---|---|---|
| Clock | 1.44 s | 0.22 s | 2.4% | 78 MB | 74 MB |
| Glass | 1.32 s | 0.19 s | 3.3% | 71 MB | 59 MB |
| Postcard | 1.22 s | 0.21 s | 3.0% | 112 MB | 63 MB |
| Type | 1.30 s | 0.20 s | 3.2% | 69 MB | 57 MB |

- **Cold start** is the first start after the app was stopped; **Home** is
  pressing Home in another app. Both count until the first frame is drawn.
- **Idle CPU** is one core's share while the home screen just sits there: the
  photo changes and its slow zoom moves in half-second steps you can't see
  (redrawn 60 times a second it used to cost about 20%).
- While moving along the dock, 90% of frames take under 20-60 ms depending on
  the theme.
- The release APK is 6.6 MB.

Next to Google TV's own home screen on the same TV, measured the same way
(`scripts/measure-stock.sh`). The TV had already been cleaned up with
`scripts/debloat.sh` (16 apps switched off), so this compares the two home
screens, not a TV as it comes from the shop:

| Home screen | Cold start | Home | Idle CPU | Memory on screen | Behind another app |
|---|---|---|---|---|---|
| Google TV | 0.60 s | 0.17 s | 0.0% | 96 MB | 95 MB |
| Glass Home (Type) | 1.34 s | 0.19 s | 4.2% | 67 MB | 55 MB |

Google TV's home screen starts faster from cold and does nothing while it
sits still; pressing Home takes about the same time with either. Glass Home
uses about a third less memory on screen and 40 MB less behind other apps
(Google TV also runs a second background process of its own).

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
  on a colour button, wallpaper, Glass Home settings, set up from your phone.
- **Back** closes a menu; on the home screen it does nothing.
- **Phone website**: hold OK on any tile > Set up from your phone. The TV
  shows a QR code, its address and a code. **New code** there signs every
  phone out.

<p align="center">
  <img src="docs/images/tile-menu.jpg" width="820" alt="The menu after holding OK on YouTube: move right, change icon, put on a colour button, hide from the dock, wallpaper, set up from your phone">
</p>

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
- **Developer options > Don't keep activities** should be off. With it on,
  Android closes every app the moment you leave it, so pressing Home builds
  the home screen again each time (0.6 s instead of 0.15 s on the test TV)
  and every other app starts from scratch too. Our test TV came with it on.
  Turn it off in Settings > System > Developer options; changing it with
  `adb shell settings` only takes effect after a reboot.
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

Guessing the code is limited: 5 wrong codes lock that phone out for 5 minutes,
and 20 wrong codes from any phones lock sign-in for everyone and replace the
code (each lockout in a row lasts twice as long). Phones stay signed in for 30
days.

## Credits and licences

- Code: MIT, see [LICENSE](LICENSE). Based on Glass Home for Samsung TVs by
  victaphu ([MIT](licenses/MIT-glass-home-samsung-tv.txt)).
- Photos: CC0, see [CREDITS.md](CREDITS.md).
- Fonts (Instrument Serif and the other clock fonts, Bricolage Grotesque and
  Atkinson Hyperlegible for the settings website): SIL Open Font License, see
  [licenses](licenses) and [CREDITS.md](CREDITS.md).
- Not affiliated with Google, Sony, Microsoft, Nintendo, Apple or any app shown.
