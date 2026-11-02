# Speed and memory

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
- **Developer options > Don't keep activities** should be off: with it on,
  every Home press builds the home screen again (0.6 s instead of 0.15 s on
  the test TV). The test TV came with it on.

## Measure your own TV

```sh
scripts/measure/compare.sh before    # the TV as it is, 3 runs, about 7 minutes
scripts/setup.sh
scripts/measure/compare.sh after     # prints the before/after table
```

## More room on a Google TV

What else kept memory on the test TV after the set-up. Check yours with
`adb shell dumpsys meminfo` (the "Total PSS by process" list).

| What | Memory | Can it go? |
|---|---|---|
| Google Assistant (`com.google.android.katniss`) | about 65 MB | If you don't use the microphone button: `scripts/cleanup/debloat.sh` offers it as a feature |
| Chromecast built-in (`com.google.android.apps.mediashell`) | about 25 MB | If you don't cast from a phone: `scripts/cleanup/debloat.sh` |
| Apps Google TV installs during set-up (Apple TV, Max, Disney+, Prime Video...) | not running until used | Hold OK on the tile > Uninstall |
| Google TV's own home screen | about 120 MB | No: switching it off breaks the TV. It stays in memory next to Glass Home |
| Netflix and other apps you used | until Android needs the room | Nothing to do: Android unloads them when it needs the memory |

A plain screensaver (`scripts/setup.sh --screensaver colors`) keeps Google's
photo screensaver off the network while the TV is idle.
