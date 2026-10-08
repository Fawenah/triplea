# TripleA: Open Source Strategy Game Engine

![Game Board Screenshot](https://user-images.githubusercontent.com/12397753/36015523-a4e28a24-0d23-11e8-84c0-c4bd0ee19ce0.png)

## About TripleA

TripleA is a fan-created, open-source strategy and board game engine that brings Axis and Allies-style gameplay to life. Launched in 2002, TripleA offers:

- Community-created maps
- Well-developed AI for single-player experiences
- Active lobby for live multiplayer games
- Diverse scenarios ranging from World War II to fantasy realms

## Key Features

- **Historical Scenarios**: Recreate pivotal moments in history, such as:
  - The Axis push towards Moscow in WWII
  - Napoleon's march across Europe
  - Rome's conquest of the Carthaginian Empire
- **Fantasy Realms**: Dive into imaginary worlds, like Sauron's conquest of Middle Earth
- **Customizable Gameplay**: Enjoy a wide variety of community-created maps and scenarios

![Europe Scenario](https://user-images.githubusercontent.com/12397753/132109225-71e6c02d-425e-4b8d-9537-7ac66baebbfd.jpeg)

![Middle Earth Scenario](https://user-images.githubusercontent.com/12397753/132109223-14a0aa2e-a950-4a5e-9937-3c4b52211cd9.jpeg)

## Getting Started

### Download
Get the latest version of TripleA from our official website:
[Download TripleA](http://triplea-game.org/download/)

### Community and Support
- [Forums: Discussion, Questions & Help](https://forums.triplea-game.org/category/10/help-questions)
- [Bug Tracker](https://github.com/triplea-game/triplea/issues/new)

## Contributing

We welcome contributions from the community! Whether you're a developer, designer, or enthusiast, there are many ways to help improve TripleA:

- [How to Contribute to TripleA](/docs/contribute.md)
- [Developer Setup Guide](/docs/development/README.md)

## License

TripleA is open-source software licensed under the GNU General Public License v3.0.

[![TripleA license](https://img.shields.io/github/license/triplea-game/triplea.svg?style=flat-square)](https://github.com/triplea-game/triplea/blob/main/LICENSE)

### Additional Permissions

Under GNU GPL version 3 section 7, we grant additional permission to convey the resulting work when combining or linking the Program with the following libraries (or modified versions of these libraries):

| Library | Group ID | Artifact ID | SPDX License ID |
|:--------|:---------|:------------|:----------------|
| Jakarta Mail | com.sun.mail | jakarta.mail | GPL-2.0-only |

## Acknowledgments

### YourKit Profiler

[![YourKit logo](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com)

YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/), and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

We're grateful to YourKit for granting the TripleA development project an open source license for YourKit Java Profiler.


## Local variant: Global 1940 MOD ECR v3.0

This repository also contains a local Axis & Allies Global 1940 variant based on
Young Grasshoppers Tournament Edition and the MOD ECR v3.0 custom rules. Use the
modified engine in this repository together with the bundled custom map: some
rules depend on engine changes as well as the map XML.

### Installation on Windows

1. Clone or extract this repository, keeping its directory structure intact.
2. Install **JDK 25**. The launcher prefers a portable JDK under
   `build/tools/jdk25/` when one is available. Otherwise, set `JAVA_HOME` to your
   JDK 25 installation in PowerShell, replacing the example path below:

   ```powershell
   $env:JAVA_HOME = 'C:\Path\To\Your\jdk-25'
   ```

3. Open PowerShell in the repository root, the folder containing `gradlew.bat`
   and this README. Launch the modified desktop client:

   ```powershell
   .\scripts\mod_ecr\run.ps1
   ```

The script builds the desktop distribution if it is missing. The first build
needs internet access to download Gradle and dependencies; Gradle is supplied
through the repository wrapper. The launcher checks for Java 25 and runs Java
directly to avoid the Windows command-length issue with the batch launcher.
Its Java and Gradle environment changes are restored when it exits.

To validate Java and the desktop classpath without opening the game window:

```powershell
.\scripts\mod_ecr\run.ps1 -CheckLaunch
```

After updating engine sources, rebuild the existing desktop distribution before
launching again. For this Gradle command, `JAVA_HOME` must point to JDK 25 even
if the launch script normally finds a portable JDK automatically:

```powershell
.\gradlew.bat :game-headed:installDist --console=plain
.\scripts\mod_ecr\run.ps1
```

### Selecting and using the map

1. In TripleA settings, set the maps folder to this repository's `custom_maps`
   directory. The launch script prints the full path.
2. Select **Global 1940 MOD ECR v3.0**, configure the players, and start the game.
3. Read the in-game notes and the bundled
   [rule status](custom_maps/global_1940_mod_ecr/global_1940_mod_ecr/RULE_STATUS.md)
   for automated rules, manual actions, and remaining limitations.

Alternatively, copy the complete map directory containing `map.yml`,
`unit-spec.json`, and the `map` folder into your configured TripleA maps folder.
In this checkout, that directory is
`custom_maps/global_1940_mod_ecr/global_1940_mod_ecr/`. Copy its assets along with
its XML. A packaged map, when built, is available at
`build/distributions/global_1940_mod_ecr-0.1.0.zip`; it still requires this
modified engine.

Gameplay details to keep in mind:

- Germany starts with 17 counted ground units against its stacking limit of 15.
  Move at least two counted units out before ending its first Combat Move.
- At Purchase start, each eligible destroyer zone offers a naval mine for 2 IPC.
  Select the displayed destroyer to lay it, or **None** to skip. Mine attacks
  happen automatically during enemy entry/transit; rolled movement cannot be
  undone.
- Free bomber-to-transport/cargo conversion still uses Edit mode before/during
  movement. These aircraft automatically revert at the end of Noncombat Move.
- AI ships trigger mines, but AI mine purchasing and route planning remain
  deferred. AI tactical bombers use normal attacks. Research progression and
  some special rules still require manual handling or remain deferred.

The [map README](custom_maps/global_1940_mod_ecr/global_1940_mod_ecr/README.md)
and [implementation plan](docs/development/mod-ecr-implementation-plan.md)
provide further details. Starting setups, rule documents, and the original YG
map are retained as local reference inputs.

### Short change summary

- Added the ECR roster and unit values, including reconnaissance, transport/cargo
  aircraft, mines, and upgrade variants; combat uses d10 while bombing damage
  and convoy rolls remain d6.
- Updated map values, movement connections, national objectives, persistent
  victory scoring, and end-of-round-eight results.
- Added land stacking limits, railroad and truck movement, airlift support,
  improved transport manifests, and factory production limits.
- Added anti-tank casualty priority, strategic bomber withdrawal, simultaneous
  fighter interception, and tactical bomber category targeting.
- Automated end-of-turn capital-ship bounties and naval mine placement/attacks,
  including ship damage, transport cargo losses, and saved-game accounting.
