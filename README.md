# 3D Worms

<img src="src/main/resources/se/hirt/bubb/worm/worm-icon.png" alt="A smiling mint-green worm with big googly eyes" width="200" height="200">

A JavaFX 3D worm game with single-player, AI duel, and LAN multiplayer modes.

## Run on the JVM

Use Java 21 and Maven:

```powershell
mvn javafx:run
```

## Build a native release

Native images require GraalVM 21 with Native Image, Maven 3.8.8, and the platform's C/C++ toolchain. Windows builds need Visual Studio 2022's Desktop development with C++ workload and an x64 Native Tools environment. The release workflow packages Windows installers with Inno Setup 6. macOS builds need Xcode Command Line Tools. Linux builds need GCC plus GTK, X11, ALSA, and FFmpeg development headers.

Build a native executable for the current host:

```powershell
mvn -Pnative-release -DskipTests clean package
```

Create a macOS installer after building the native image:

```powershell
# macOS disk image
mvn -Pnative-release -Dgluonfx.packageType=dmg gluonfx:package
```

The tag release workflow builds Windows x64 and macOS arm64 installers, plus a Linux x64 archive. To publish a release, push a version tag such as `v1.0.0`.

To recover a failed release with corrected build infrastructure, run the workflow from `main` against the existing source tag:

```powershell
gh workflow run release.yml --ref main -f release_tag=v1.0.0
```

This checks out the tagged sources and publishes artifacts for that tag without moving it.

If GitHub cannot allocate an Apple Silicon runner, use the Intel macOS runner pool for the recovery run:

```powershell
gh workflow run release.yml --ref main -f release_tag=v1.0.0 -f macos_arch=x86_64
```

That run labels its Mac download `macos-x86_64`; tag-triggered releases still build `macos-aarch64` by default.

Release packages are intentionally unsigned. Windows releases include both a portable executable and a setup installer; macOS releases include a DMG; Linux releases include a tar.gz containing the native executable.
