# 3D Worms

A JavaFX 3D worm game with single-player, AI duel, and LAN multiplayer modes.

## Run on the JVM

Use Java 21 and Maven:

```powershell
mvn javafx:run
```

## Build a native release

Native images require GraalVM 21 with Native Image, Maven 3.8.8, and the platform's C/C++ toolchain. Windows builds need Visual Studio 2022's Desktop development with C++ workload and an x64 Native Tools environment. Windows installers also need WiX 3 or later. macOS builds need Xcode Command Line Tools. Linux builds need GCC plus GTK and X11 development headers.

Build a native executable for the current host:

```powershell
mvn -Pnative-release -DskipTests clean package
```

Create an installer after building the native image:

```powershell
# Windows setup executable
mvn -Pnative-release -Dgluonfx.packageType=exe gluonfx:package

# macOS disk image
mvn -Pnative-release -Dgluonfx.packageType=dmg gluonfx:package
```

The tag release workflow builds Windows x64 and macOS arm64 installers, plus a Linux x64 archive. To publish a release, push a version tag such as `v1.0.0`.

Release packages are intentionally unsigned. Windows releases include both a portable executable and a setup installer; macOS releases include a DMG; Linux releases include a tar.gz containing the native executable.
