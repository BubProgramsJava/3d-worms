# 3D Worms

<img src="src/main/resources/se/hirt/bubb/worm/worm-icon.png" alt="A smiling mint-green worm with big googly eyes" width="200" height="200">

A JavaFX 3D worm game with single-player, AI duel, and LAN multiplayer modes.

## Run on the JVM

Use Java 21 and Maven:

```powershell
mvn javafx:run
```

## Build a native release

Windows native releases use GraalVM Native Image 25 directly, with standard OpenJFX libraries and checked-in JavaFX reachability metadata. Set `JAVA_HOME` and `GRAALVM_HOME` to GraalVM 25 and build from an x64 Visual Studio Native Tools environment with the Desktop development with C++ workload installed:

```powershell
mvn -Pnative-windows -DskipTests clean package
.\target\native\3d-worms.exe
```

The Windows executable embeds the JavaFX DLLs, shaders, styles, and icon; it does not require a separate Java installation. The release workflow packages it with Inno Setup 6. See [Windows native build notes](docs/native-windows.md) for metadata collection and diagnostics.

macOS native releases also use GraalVM Native Image 25 directly, with their own checked-in JavaFX reachability metadata. Set `JAVA_HOME` and `GRAALVM_HOME` to GraalVM 25 and install the Xcode Command Line Tools:

```bash
mvn -Pnative-macos -DskipTests clean package
config/native/macos/package-app.sh --version 1.0.0
open dist/3d-worms-1.0.0-macos-aarch64.dmg
```

Native Image produces a bare executable, so `package-app.sh` wraps it in `3D Worms.app` with the icon and `Info.plist` and writes the disk image. Run the executable through the bundle rather than directly, because Glass asks macOS for the bundle's name on startup. See [macOS native build notes](docs/native-macos.md) for metadata collection, the main-thread launcher, and diagnostics.

Linux native releases still use GluonFX with GraalVM 21, Maven 3.8.8, GCC, and the GTK, X11, ALSA, and FFmpeg development headers:

```bash
mvn -Pnative-release -DskipTests clean package
```

The tag release workflow builds Windows x64 and macOS arm64 installers, plus a Linux x64 archive. To publish a release, push a version tag such as `v1.0.0`.

To recover a failed release with corrected build infrastructure, run the workflow from `main` against the existing source tag:

```powershell
gh workflow run release.yml --ref main -f release_tag=v1.0.0
```

This checks out the tagged sources and publishes artifacts for that tag without moving it.

If GitHub cannot allocate a macOS runner, publish the Windows and Linux downloads with:

```powershell
gh workflow run release.yml --ref main -f release_tag=v1.0.0 -F include_macos=false
```

This recovery run omits the Mac download. Run the workflow again with its default inputs once Apple Silicon runners are available to add the DMG to the same release. Tag-triggered releases always build all three platforms; macOS builds use the pinned `macos-15` ARM64 image.

Start a new workflow run from `main` after pushing workflow fixes. Re-running an old failed run uses its original workflow definition.

The Windows and macOS Native Image 25 builds require a source tag that contains their profiles, launchers, and metadata. For these migrations, create a new version tag on the updated sources; a recovery run against the original sources cannot add those changes.

Release packages carry no Developer ID signature, so macOS shows an unidentified-developer warning on first launch. The macOS app bundle is ad-hoc signed, which Apple Silicon requires in order to run it at all. Windows releases include both a portable executable and a setup installer; macOS releases include a DMG; Linux releases include a tar.gz containing the native executable.
