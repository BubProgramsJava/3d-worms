# Windows Native Image build

The `native-windows` Maven profile builds `target/native/3d-worms.exe` with GraalVM Native Image 25 and standard OpenJFX 21.0.6. It does not invoke GluonFX or use its static JavaFX SDK. Linux and macOS still use the separate `native-release` profile until their JavaFX metadata has been collected and verified.

## Toolchain and build

Install GraalVM 25 with Native Image and Visual Studio's Desktop development with C++ workload. Use an x64 Native Tools prompt and set both `JAVA_HOME` and `GRAALVM_HOME` to that GraalVM installation. Maven must also run on Java 25.

```powershell
java --version
native-image --version
mvn --version
mvn -Pnative-windows -DskipTests clean package
.\target\native\3d-worms.exe
```

The tested local toolchain was Oracle GraalVM 25.0.4+7.1 and MSVC 19.52.37029. The release workflow uses the Windows 2022 runner's x64 C++ toolchain and GraalVM 25.

`Launcher` calls `Application.launch(Main.class, args)` without extending `Application`, so it works as a normal classpath entry point for both JVM tracing and Native Image. The executable embeds JavaFX DLLs and resources, which JavaFX loads through its native-library loader. No installed JDK is needed at runtime.

## Windows icons

The JavaFX window uses `src/main/resources/se/hirt/bubb/worm/worm-icon.png`. Explorer and shortcuts need a separate Windows icon resource embedded in the executable. `config/native/windows/worm-icon.ico` is derived from that PNG and contains 16, 32, 48, 64, 128, and 256 pixel images.

During `prepare-package`, the Windows SDK resource compiler (`rc.exe`, available in the x64 Native Tools environment) compiles `config/native/windows/worm-icon.rc` into `target/worm-icon.res`. Native Image passes this resource to the Windows linker. The release workflow also uses the ICO as Inno Setup's `SetupIconFile`; installed shortcuts and the uninstall entry use the executable's embedded icon.

When replacing the artwork, update both the PNG and ICO. Build a new release tag containing these files and the Maven configuration; recovery builds of older source tags cannot embed the new executable icon.

## Reachability metadata

`config/native/windows/reachability-metadata.json` was collected with GraalVM 25's tracing agent while running this application on Windows with the Direct3D pipeline. Its JNI and reflection registrations cover the Glass window system, DirectWrite fonts, JavaFX controls, and Direct3D renderer. Resource globs additionally include JavaFX DLLs, all shaders and styles, resource bundles, and the application icon rather than only assets exercised during tracing.

All locales are included because JavaFX's startup logging can load locale bundles that were not observed by the agent. Exact reachability metadata is enabled to expose missing registrations instead of hiding them.

When JavaFX or the application's dynamic UI changes, collect and merge fresh metadata with GraalVM 25. From PowerShell, using the same dependency versions as the native build:

```powershell
mvn -DskipTests package dependency:build-classpath "-Dmdep.outputFile=target/native-classpath.txt"
$runtimeClasspath = (Get-Content target/native-classpath.txt -Raw).Trim()
java --enable-native-access=ALL-UNNAMED `
    -agentlib:native-image-agent=config-merge-dir=config/native/windows `
    -cp "target/classes;$runtimeClasspath" se.hirt.bubb.worm.Launcher
```

Exercise the main menu, gameplay, settings, shop, and LAN screens, then quit normally so the agent writes the metadata. Review the resulting JSON before committing it and rebuild the native executable. Metadata is platform-specific; a Windows trace is insufficient for Linux or macOS.

## Runtime diagnostics

```powershell
.\target\native\3d-worms.exe -Dprism.verbose=true -XX:MissingRegistrationReportingMode=Exit
```

The tested executable initialized `com.sun.prism.d3d.D3DPipeline` on the local NVIDIA GPU and rendered the menu and game correctly. Keep the shader resources and JNI registrations when updating the build; compilation alone does not verify rendering.
