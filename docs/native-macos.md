# macOS Native Image build

The `native-macos` Maven profile builds `target/native/3d-worms` with GraalVM Native Image 25 and standard OpenJFX 21.0.6. It does not invoke GluonFX or use its static JavaFX SDK. Linux still uses the separate `native-release` profile until its JavaFX metadata has been collected and verified.

## Toolchain and build

Install GraalVM 25 with Native Image and the Xcode Command Line Tools, then point both `JAVA_HOME` and `GRAALVM_HOME` at that GraalVM. Maven must also run on Java 25.

```bash
java --version
native-image --version
mvn -Pnative-macos -DskipTests clean package
config/native/macos/package-app.sh --version 1.0.0
```

The tested local toolchain was Oracle GraalVM 25.0.5.0.3+1.1 on macOS 27 and an Apple M4. The release workflow uses the pinned `macos-15` ARM64 runner with the same GraalVM major version. Only `aarch64` is built; `package-app.sh` names the disk image after `uname -m`, so an Intel host produces an `x86_64` image instead.

## Why there is a separate launcher

`MacLauncher`, not `Launcher`, is the image's entry point. The `java` launcher runs `main` on a secondary thread and keeps the process main thread pumping a Cocoa run loop, which is what lets `Application.launch` work there: Glass posts its run loop onto that main thread. A native executable has no such launcher. `main` owns the main thread, so Glass posts the run loop to a thread that is blocked inside `launch`, and the window never appears while the process sits there alive and idle. `MacLauncher` calls `Platform.startup` from the main thread instead, which makes it the JavaFX application thread and hands Glass the thread it needs.

`MacLauncher` also leaves the process with `Runtime.halt` as soon as the game's window gets a close request. JavaFX's macOS shutdown clears the Glass event thread while AppKit is still delivering window-close notifications, and those callbacks then fail their event-thread check and crash the process. The JVM crashes the same way, with `Java has been detached already` from `GlassWindow+Overrides.m`, so this is not a Native Image problem and cannot be fixed from the application. Both quit routes raise a close request first, the window button directly and the macOS Quit command through Glass asking every window to close, so exiting there is reliable. Everything the game persists is written synchronously as it changes and `Main` closes its LAN sockets in its own handler, which runs first.

## App bundle and disk image

Native Image emits a bare executable, so `config/native/macos/package-app.sh` assembles `3D Worms.app` from the executable, `worm-icon.icns`, and `Info.plist`, then writes a compressed disk image containing the bundle and an `Applications` symlink. Regenerate the icon with `make-icon.sh` after changing the stage icon PNG; `iconutil` rejects an incomplete icon set, so that script renders every standard size.

Run the game through the bundle. Glass asks macOS for the bundle's application name during startup, and that call only happens when the executable sits inside an `.app`, which is why a bare executable can pass while the shipped bundle fails. Releases carry no Developer ID, but the bundle is ad-hoc signed because Apple Silicon refuses to run an unsigned bundle at all. Signing happens after the executable is copied in, since copying it into the bundle would otherwise invalidate the signature.

## Reachability metadata

`config/native/macos/reachability-metadata.json` was collected with GraalVM 25's tracing agent on macOS with the ES2 pipeline. Its JNI and reflection registrations cover the Mac Glass window system, CoreText fonts, JavaFX controls, and the ES2 renderer. Metadata is platform specific: a Windows trace does not describe the Mac Glass and CoreText classes, and a macOS trace does not describe DirectWrite or Direct3D.

Three groups of entries go beyond what the agent observed, because the agent only records the branches a single session happens to take.

- The `com.sun.glass.ui` and `com.sun.javafx.font.coretext` classes are registered whole, with all declared constructors, fields, and methods. They exist as a JNI callback surface, and Glass's Objective-C layer resolves their members lazily with `GetMethodID`. Which members it asks for depends on how the app started, so a traced classpath run misses the bundle, menu, and clipboard lookups a packaged app makes.
- Resource globs include all `.dylib`, `.frag`, `.vert`, `.png`, `.css`, `.bss`, and `.properties` files rather than only the ones loaded during tracing, so JavaFX shaders and styles cannot go missing.
- `sun/util/logging/resources/*.properties` is registered against the `java.logging` module. JavaFX logs an unsupported-configuration warning at startup because its classes come from the classpath, and formatting that warning loads a locale bundle. A plain glob never matches a module's own resources, so the module has to be named. All locales are included for the same reason.

When JavaFX or the application's dynamic UI changes, collect and merge fresh metadata with GraalVM 25:

```bash
mvn -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/native-classpath.txt
CP="target/classes:$(cat target/native-classpath.txt)"
mkdir -p target/trace-classes
javac -cp "$CP" -d target/trace-classes config/native/macos/TraceDriver.java
java --enable-native-access=ALL-UNNAMED \
    -Duser.home="$(mktemp -d)" \
    -agentlib:native-image-agent=config-merge-dir=config/native/macos \
    -cp "target/trace-classes:$CP" TraceDriver --quit=exit
```

`TraceDriver` walks the UI instead of leaving it to be clicked through by hand: it names the player, remaps a control, buys and wears customizations, plays a duel and a single-player round with camera orbiting, opens LAN discovery and hosts a game, and uses the pause menu, then quits. A throwaway `user.home` keeps the traced session away from a real player profile. `--quit=exit` is needed here because the agent writes its metadata from a JVM shutdown hook, which the launcher's immediate exit on close would skip; that run ends with JavaFX's unclean toolkit shutdown, after the metadata has been written. Review the resulting JSON before committing it, and re-apply the three deliberate groups above if the agent's merge drops them.

## Verifying a build

Compilation proves nothing about rendering, and neither does a bare executable that starts without complaining. Build `TraceDriver` as a second native image to re-walk every screen inside a real executable:

```bash
mkdir -p target/native-trace
native-image --no-fallback --enable-native-access=ALL-UNNAMED --exact-reachability-metadata \
    -march=compatibility -H:+IncludeAllLocales \
    -H:ConfigurationFileDirectories=config/native/macos \
    -cp "target/trace-classes:$CP" -o target/native-trace/3d-worms-trace TraceDriver
./target/native-trace/3d-worms-trace -Duser.home="$(mktemp -d)" -XX:MissingRegistrationReportingMode=Exit
```

It should print its 21 steps, finish in about half a minute, and exit 0. `MissingRegistrationReportingMode=Exit` turns a missing registration into an immediate failure rather than a silent null. Exact reachability metadata is enabled in the build for the same reason.

Then check the packaged bundle, which is the only way to catch the bundle-only JNI lookups:

```bash
./target/native/3d-worms --version 2>/dev/null || true
open target/macos-bundle/3D\ Worms.app
lsappinfo list | grep -A3 '"3D Worms"'
```

The tested executable initialized `com.sun.prism.es2.ES2Pipeline` on the local GPU, rendered the menu and game, and registered with Launch Services as `se.hirt.bubb.worm`.

## Runtime diagnostics

```bash
"target/macos-bundle/3D Worms.app/Contents/MacOS/3d-worms" \
    -Dprism.verbose=true -XX:MissingRegistrationReportingMode=Exit
```

If the window never appears and the process idles, sample it: a main thread parked in `_pthread_cond_wait` while a `JavaFX-Launcher` thread sits in `MacApplication._runLoop` means the toolkit was started off the main thread again.

One trap is not covered by the metadata on purpose. The full-screen exit hint overlay, `com.sun.javafx.tk.quantum.OverlayWarning`, is never constructed because `Main` sets the full-screen exit key combination to `NO_MATCH`. Change that key combination and the overlay becomes reachable, and it needs its own registration.
