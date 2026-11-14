package se.hirt.bubb.worm;

import javafx.application.Application;

/** Shared JVM and Native Image entry point, without the JVM's JavaFX launcher hook. */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
