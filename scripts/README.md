# Scripts

Everything runs from the repository folder and finds the TV by itself
(`TV=<serial>` picks one when there are several).

| Folder | What is in it |
|---|---|
| `setup.sh`, `restore.sh` | The two you need: set the TV up, and put it back |
| `tv/` | Pair the computer (`pair.sh`), find the TV (`connect.sh`), take a screenshot (`shot.sh`) |
| `install/` | Glass Home itself: `install-apk.sh`, `deploy.sh` (build and install), `set-home.sh`, `enable-now-playing.sh`, `allow-updates.sh` |
| `cleanup/` | The TV clean-up: `debloat.sh`, `speed.sh` (animations), `private-dns.sh` |
| `backup/` | `backup.sh`: the TV's state, before any change |
| `measure/` | Speed and memory: `compare.sh` (before/after), `themes.sh`, `home-key.sh`, `stock.sh` |
| `dev/` | For working on Glass Home: `release.sh`, `readme-media.sh`, official logos |
| `lib/` | `changelog.sh`, shared by the scripts that change the TV |

Every change to the TV is written to `.tv/changes.log`; `restore.sh` undoes it.
