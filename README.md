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
- [Set up](#set-up) (install on your TV) and [going back](#going-back)
- [Using it](#using-it)
- [Speed and memory](#speed-and-memory)
- [Good to know](#good-to-know), [More room on a Google TV](#more-room-on-a-google-tv) and [Privacy](#privacy)

## What you get

- **Four themes**: Clock (big serif clock, small icons), Glass (frosted dock),
  Postcard (the photo as a card, an app menu) and Type (words instead of
  icons).
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
- **Updates from GitHub, only when you say so**: when a new release is out,
  the home screen asks Install, Later or Skip this version. No store, no
  other app; checks can be switched off on the website.
- **In English, Ukrainian or Belarusian**, on the TV and the website.
- **Light**: 35 MB of memory while another app is open; Google TV's home
  screen keeps 124 MB ([measured](#speed-and-memory)).

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
- **Photos**: add photos, shuffle or your own order, how long each one stays,
  slow zoom on or off.
- **Apps**: the dock in order, hide or show, and the colour buttons.
- **More**: language, the Home button, the weather city, a shared iCloud or
  Google Photos album, updates, icons.

It follows the phone's light or dark setting; the sun/moon button at the top
switches it, and the phone remembers the choice.

<p align="center">
  <img src="docs/images/web-look.png" width="300" alt="The phone website in light mode: the live preview of the TV with the Alps photo and 17:55, the tabs, and the Look tab with four theme cards over the same photo, Clock selected">
  &nbsp;&nbsp;
  <img src="docs/images/web-apps.png" width="300" alt="The phone website in dark mode: the live preview and the Apps tab, apps in order with up, down and hide buttons, Live TV hidden">
</p>

## Settings on the TV

No phone at hand? Open **Home settings**, the last tile on the dock in every
theme (the same as **Glass Home settings** in the menu when you hold **OK**). The same
website opens on the TV, already signed in, with the tabs down the left. Arrows move between
controls, **OK** picks, **Back** closes it. Adding photos and icons needs a
phone (files come from there); everything else works with the remote.

<p align="center">
  <img src="docs/images/tv-settings.jpg" width="820" alt="The settings website on the TV: the live preview and the tabs on the left, the theme cards on the right with Glass outlined by the remote's yellow focus ring and Clock marked as the current theme">
</p>

## Set up

You need a computer with [adb](https://developer.android.com/tools/releases/platform-tools)
on the TV's Wi-Fi. **Step by step, with what to do when something goes wrong:
[docs/INSTALL.md](docs/INSTALL.md).** The short version:

1. On the TV: Settings > System > About > click **Android TV OS build** (or
   **Build**) 7 times, then
   Settings > System > Developer options > **Wireless debugging** on.
2. Pair the computer once: on the TV, Wireless debugging > **Pair device with
   pairing code** shows an address and a code. Give both to the script:

   ```sh
   scripts/tv/pair.sh 192.168.1.20:37000 123456
   ```

3. Run the set-up. It backs up the TV first, asks what to set up, then asks
   about each step; Enter takes the recommended answer.

   ```sh
   scripts/setup.sh           # asks
   scripts/setup.sh --yes     # everything, recommended answers, no questions
   ```

Glass Home and the clean-up don't need each other. Pick one or both:

| What you want | Command | What happens |
|---|---|---|
| Both (recommended) | `scripts/setup.sh` | Glass Home as the home screen, and ads, tracking and junk switched off |
| Only Glass Home | `scripts/setup.sh --only glasshome` | Glass Home, Home button and now playing. Nothing on the TV is switched off |
| Only the clean-up | `scripts/setup.sh --only clean` | Ads, tracking, animations, DNS, screensaver. Glass Home is not installed; the TV keeps its own home screen |
| Just the APK | `scripts/install/install-apk.sh glass-home.apk` | Installs Glass Home and lets it install its own updates; no backup, Home button or clean-up. Open it from the apps list; `scripts/install/set-home.sh` later puts it on Home |

`glass-home.apk` comes from the
[Releases](https://github.com/MikeKiselyov/glass-home-android/releases) page;
add `--apk glass-home.apk` to `setup.sh` to use it instead of building.

Every question can be answered with an option instead, for example
`--home off` or `--clean ask`: [the list](docs/INSTALL.md#4-run-the-set-up).

Each step is also its own script in [scripts](scripts) ([what is where](scripts/README.md)). Nothing is ever
uninstalled: apps are switched off with `pm disable-user` and come back with
`pm enable`.

### Going back

- **A backup before every change.** `setup.sh` and `restore.sh` save the TV's
  state to `.tv/backup/<date-time>/` first: every app and whether it is on,
  all system settings, the Home app, a screenshot and a copy of the change
  log. Old backups are never deleted.
- **Every change is written down** in `.tv/changes.log` with the value it
  replaced.
- **One command undoes it all**, newest change first:

  ```sh
  scripts/restore.sh --dry-run    # shows what it would do
  scripts/restore.sh              # does it; Glass Home stays installed
  scripts/restore.sh --uninstall  # also removes Glass Home
  ```

- **Without the computer**: Settings > Accessibility > Glass Home off gives the
  Home button back to Google TV; Settings > Apps > (app) > Enable brings back
  anything that was switched off.

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
  on a colour button, uninstall, app info, wallpaper, Glass Home settings,
  set up from your phone.
- **Uninstall** is there for apps you (or Google TV) installed; Android asks
  before removing anything. Apps built into the TV can't be removed: **App
  info** opens the system page where they can be switched off, stopped or
  have their updates removed.
- **Back** closes a menu; on the home screen it does nothing.
- **New code** on the Set up from your phone panel signs every phone out.

<p align="center">
  <img src="docs/images/tile-menu.jpg" width="820" alt="The menu after holding OK on YouTube: move right, change icon, put on a colour button, hide from the dock, wallpaper, set up from your phone">
</p>

### Official logos

Icons for PlayStation, Xbox, Nintendo Switch and Apple TV are drawn from
scratch. If you'd rather have the brand logos on your own TV:

```sh
python3 scripts/dev/make-official-icons.py   # downloads logos from Wikimedia Commons (macOS)
scripts/dev/push-icons.sh                    # debug builds; or upload the PNGs on the phone website
```

The logos are trademarks of their owners. They stay on your computer and
your TV and are never part of this repository or the APK.

## Speed and memory

Measured on one TV (Smarttech Apollo Premium 4K, Google TV, Android 14, 4
cores, 2 GB RAM) after a factory reset, set up the way a buyer would. A reboot
before each state, the same script for all of them, Glass Home's release
build with the Clock theme:

| | Factory, Google TV | Clean-up, Google TV | Clean-up + Glass Home |
|---|---|---|---|
| Home screen memory, on screen | 126 MB | 130 MB | 45 MB |
| Home screen memory, behind another app | 119 MB | 124 MB | 35 MB |
| Idle CPU, home screen (one core) | 0.2% | 0.1% | 2.1% |
| Cold start: first frame / tiles on screen | - | - | 0.38 / 0.41 s |
| Home key to the home screen | - | - | 0.06 s |
| Frames for the same arrow keys: drawn, slower than 60 per second | 228, 201 | 215, 182 | 25, 5 |
| Free memory on the TV | 542 MB | 535 MB | 557 MB |
| Google TV's launcher in memory | 130 MB | 135 MB | 94 MB |
| Apps running | 41 | 25 | 24 |
| Idle CPU, whole TV | 6.1% | 5.6% | 5.7% |

Glass Home's first five rows are release 0.3.0, 3 runs; the last five rows
come from the 0.2.0 run on the freshly reset TV.

- **Clean-up** is `scripts/setup.sh --only clean --yes`: 8 ad, tracking and
  demo packages switched off and animations at half length. It barely changes
  memory or CPU; fewer apps run in the background.
- **Free memory stays about the same**: Glass Home takes about a third of
  what Google TV's home screen takes, but Google TV can't be removed. It
  starts once at boot and stays in memory.
- **Home key** is from the key press to Glass Home's first frame; Google TV
  doesn't start on the way.
- **Frames**: Glass Home moves the focus without animating rows, so it draws
  about a ninth of Google TV's frames.
- **Idle CPU** is where Glass Home costs more: the photo's slow zoom. With
  **Photos > Slow zoom** off it is 1.7% (44 / 34 MB).

Measure your own TV the same way:

```sh
scripts/measure/compare.sh before    # the TV as it is, 3 runs, about 7 minutes
scripts/setup.sh
scripts/measure/compare.sh after     # prints the before/after table
```

## Good to know

- **Google TV** ignores a third-party Home app while its own launcher is on,
  and disabling the launcher or the setup wizard is risky (on the test TV it
  cut the network). So `set-home.sh` keeps both and turns on a small
  accessibility service that catches the Home key and opens Glass Home;
  Google TV's home screen doesn't start or flash. Holding Home no longer opens
  the Google TV panel. If catching the key doesn't work on your TV, set
  **More > Home button > After Google TV** on the website.
- **Force stop** on Glass Home, and some system updates, switch that service
  off. Home then opens Google TV until you run `scripts/install/set-home.sh` or turn
  it on in Settings > Accessibility > Glass Home.
- **Developer options > Don't keep activities** should be off: with it on,
  every Home press builds the home screen again (0.6 s instead of 0.15 s on
  the test TV). The test TV came with it on.
- **Google Photos albums** are read from the public share page, which Google
  can change at any time. iCloud shared albums use Apple's web API.

## More room on a Google TV

What else kept memory on the test TV after the set-up, and what can be done
about it. Check yours with `adb shell dumpsys meminfo` (the "Total PSS by
process" list).

| What | Memory | Can it go? |
|---|---|---|
| Google Assistant (`com.google.android.katniss`) | about 65 MB | If you don't use the microphone button: `scripts/cleanup/debloat.sh` offers it as a feature |
| Chromecast built-in (`com.google.android.apps.mediashell`) | about 25 MB | If you don't cast from a phone: `scripts/cleanup/debloat.sh` |
| Apps Google TV installs during set-up (Apple TV, Max, Disney+, Prime Video...) | not running until used | Hold OK on the tile > Uninstall |
| Google TV's own home screen | about 120 MB | No: switching it off breaks the TV. It stays in memory next to Glass Home |
| Netflix and other apps you used | until Android needs the room | Nothing to do: Android unloads them when it needs the memory |

A plain screensaver (`scripts/setup.sh --screensaver colors`) keeps Google's
photo screensaver off the network while the TV is idle.

## Privacy

The app talks to: Open-Meteo for weather (once you pick a city), your shared
album's host (once you add a link), GitHub once a day to look for a new release
(a few hours later again if it didn't answer; the APK itself comes from
GitHub's download servers; off with More > Updates on the website), and
nothing else. The update check
sends nothing about you or the TV beyond what any web request carries.

The accessibility service that makes Home open Glass Home sees the keys
pressed on the remote, because that is how Android lets an app catch Home. It
acts only on Home, keeps nothing, sends nothing and cannot read what is on
screen; it logs only the time Home was pressed, for the speed numbers. The
phone website only answers devices on your home network, needs the code from
the TV, and uses plain HTTP, so anyone on the same Wi-Fi could read the
traffic.

Guessing the code is limited: 5 wrong codes lock that phone out for 5 minutes,
and 20 wrong codes from any phones lock sign-in for everyone and replace the
code (each lockout in a row lasts twice as long). Phones stay signed in for 30
days.

## Languages

English, Ukrainian and Belarusian; pick one on the website under More >
Language. All text, on the TV and on the website, is in
[app/src/main/assets/i18n](app/src/main/assets/i18n): `en.json`, `ua.json`,
`by.json`. To add a language, copy `en.json` to a new file (for example
`pl.json`), set `language.name` and `language.tag` and translate the rest;
missing keys fall back to English. The app name and the accessibility
description are in `res/values/strings.xml`.

## Status

Built and used on one TV (Smarttech Apollo Premium 4K, Google TV, Android 14).
It should work on any Android TV 8.0+ or Google TV; other TVs are untested.
Reports and pull requests are welcome, especially additions to
[debloat/packages.tsv](debloat/packages.tsv) for your TV brand.

## Releases

APKs on the [Releases](https://github.com/MikeKiselyov/glass-home-android/releases)
page are signed with one permanent key, so each release installs over the
previous one. From the next release, 0.4.0, Glass Home finds new releases by itself and asks
before installing them ([how](docs/INSTALL.md#updates)). How releases are
made: [docs/RELEASING.md](docs/RELEASING.md).

## Credits and licences

- Code: MIT, see [LICENSE](LICENSE). Based on Glass Home for Samsung TVs by
  victaphu ([MIT](licenses/MIT-glass-home-samsung-tv.txt)).
- Photos: CC0, see [CREDITS.md](CREDITS.md).
- Fonts (Instrument Serif and the other clock fonts, Bricolage Grotesque and
  Atkinson Hyperlegible for the settings website): SIL Open Font License, see
  [licenses](licenses) and [CREDITS.md](CREDITS.md).
- Not affiliated with Google, Sony, Microsoft, Nintendo, Apple or any app shown.
