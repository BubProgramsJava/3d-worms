# Linux Native Image build

The `native-linux` Maven profile builds `target/native/3d-worms` with GraalVM Native Image 25 and standard OpenJFX 21.0.6. It does not invoke GluonFX or use its static JavaFX SDK.

## Toolchain and build

Install GraalVM 25 with Native Image, GCC, and the zlib development headers, then point both `JAVA_HOME` and `GRAALVM_HOME` at that GraalVM. Maven must also run on Java 25.

```bash
sudo apt-get install build-essential zlib1g-dev
java --version
native-image --version
mvn -Pnative-linux -DskipTests clean package
./target/native/3d-worms
```

The tested local toolchain was Oracle GraalVM 25.0.5.0.3+1.1 with GCC 13.3 on Ubuntu 24.04 x86_64. The release workflow uses the `ubuntu-24.04` runner with the same GraalVM major version and ships the bare executable in a tar.gz.

No GTK, X11, OpenGL, or FFmpeg development packages are needed to build. The executable links only against glibc and zlib. JavaFX's native libraries travel inside it as resources, and JavaFX extracts them to `~/.openjfx/cache` on first start and loads them from there. Those libraries load the system's GTK 3, Pango, FreeType, and OpenGL at run time, which every desktop distribution provides. A build on Ubuntu 24.04 needs glibc 2.34 or newer on the player's machine.

`Launcher` is the entry point, as on Windows. GTK, unlike Cocoa on macOS, does not need the process main thread, so `Application.launch` works unchanged in a native executable and the macOS launcher's workarounds do not apply.

## Renderers

JavaFX on Linux renders with the ES2 pipeline on OpenGL, and falls back to the software pipeline when it does not trust the GPU. The software pipeline has no 3D support, so the game world does not render there. Both pipelines are covered by the metadata, because which one a player gets depends on their drivers rather than on the build.

JavaFX rejects Mesa's software OpenGL renderer, llvmpipe, which is what a machine without working GPU drivers or a virtual machine typically offers. To use OpenGL there anyway, start the game with:

```bash
./target/native/3d-worms -Dprism.forceGPU=true
```

The game runs as an X11 client. On a Wayland desktop it uses XWayland, which is how it was tested.

## Reachability metadata

`config/native/linux/reachability-metadata.json` was collected with GraalVM 25's tracing agent on Linux, once with the ES2 pipeline and once with the software pipeline, merged into one file. Its JNI and reflection registrations cover the GTK Glass window system, FreeType and fontconfig fonts, JavaFX controls, the ES2 renderer, and the Pisces software rasterizer. Metadata is platform specific: neither the Windows nor the macOS trace describes the GTK and FreeType classes.

Three groups of entries go beyond what the agent observed, because the agent only records the branches a single session happens to take.

- The `com.sun.glass.ui` and `com.sun.glass.ui.gtk` callback classes, and the FreeType, fontconfig, and Pango structure classes, are registered whole, with all declared constructors, fields, and methods. Glass resolves most of its members when it loads, but input method, drag-and-drop, and clipboard callbacks are looked up only when a player uses them.
- Resource globs include all `.so`, `.frag`, `.vert`, `.png`, `.css`, `.bss`, and `.properties` files rather than only the ones loaded during tracing, so JavaFX libraries, shaders, and styles cannot go missing. `libjavafx_iio.so` and `libprism_common.so` are not loaded by the traced session at all.
- `sun/util/logging/resources/*.properties` is registered against the `java.logging` module, for the same unsupported-configuration warning described in the [macOS notes](native-macos.md#reachability-metadata). All locales are included for the same reason.

When JavaFX or the application's dynamic UI changes, collect and merge fresh metadata with GraalVM 25. Trace both pipelines:

```bash
mvn -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/native-classpath.txt
CP="target/classes:$(cat target/native-classpath.txt)"
mkdir -p target/trace-classes
javac -cp "$CP" -d target/trace-classes config/native/TraceDriver.java
for force in true false; do
    java --enable-native-access=ALL-UNNAMED \
        -Duser.home="$(mktemp -d)" -Dprism.verbose=true -Dprism.forceGPU=$force \
        -agentlib:native-image-agent=config-merge-dir=config/native/linux \
        -cp "target/trace-classes:$CP" TraceDriver
done
```

Each run should print `Initialized prism pipeline` with `ES2Pipeline` and `SWPipeline` respectively. On a machine with a trusted GPU, `forceGPU=false` still selects ES2; force the software pipeline there with `-Dprism.order=sw` instead.

`TraceDriver` is shared with macOS and walks every screen, as described in the [macOS notes](native-macos.md#reachability-metadata). On Linux it ends by closing the window the way a player does. JavaFX then shuts down cleanly and the agent writes its metadata, so `--quit=exit` is not needed. Review the resulting JSON before committing it, and re-apply the three deliberate groups above if the agent's merge drops them.

## Verifying a build

Compilation proves nothing about rendering. Build `TraceDriver` as a second native image and re-walk every screen inside a real executable on both pipelines:

```bash
mkdir -p target/native-trace
native-image --no-fallback --enable-native-access=ALL-UNNAMED --exact-reachability-metadata \
    -march=compatibility -H:+IncludeAllLocales \
    -H:ConfigurationFileDirectories=config/native/linux \
    -cp "target/trace-classes:$CP" -o target/native-trace/3d-worms-trace TraceDriver
for force in true false; do
    ./target/native-trace/3d-worms-trace -Duser.home="$(mktemp -d)" -Dprism.forceGPU=$force \
        -XX:MissingRegistrationReportingMode=Exit
done
```

Each run should print its 21 steps, finish in about half a minute, and exit 0. `MissingRegistrationReportingMode=Exit` turns a missing registration into an immediate failure rather than a silent null. Exact reachability metadata is enabled in the build for the same reason.

The tested executable initialized `com.sun.prism.es2.ES2Pipeline` on Mesa llvmpipe with `prism.forceGPU`, and `com.sun.prism.sw.SWPipeline` without it, and both walkthroughs passed.

## Runtime diagnostics

```bash
./target/native/3d-worms -Dprism.verbose=true -XX:MissingRegistrationReportingMode=Exit
```

`Failed Graphics Hardware Qualifier check` followed by `Fallback to Prism SW pipeline` means JavaFX rejected the OpenGL driver; see [Renderers](#renderers). If JavaFX's native libraries fail to load, delete `~/.openjfx/cache` so they are extracted again.
