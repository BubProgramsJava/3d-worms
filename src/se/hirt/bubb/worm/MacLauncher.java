package se.hirt.bubb.worm;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * macOS entry point for the GraalVM Native Image build.
 *
 * <p>The {@code java} launcher runs {@code main} on a secondary thread and leaves the process main
 * thread pumping a Cocoa run loop, which is what lets {@link javafx.application.Application#launch}
 * work there. A native executable has no such launcher: {@code main} owns the process main thread,
 * so Glass posts its run loop to that thread and then waits forever for a loop nobody pumps, and
 * the window never appears. Starting the toolkit from this thread instead makes it the JavaFX
 * application thread and hands Glass the main thread it requires.
 *
 * <p>{@link Platform#startup} replaces the {@code Application} launcher, so the game is constructed
 * and started directly. {@link Main} uses neither {@code init}, {@code stop}, nor the application
 * parameters, so nothing of its lifecycle is lost.
 */
public final class MacLauncher {
    private MacLauncher() {
    }

    public static void main(String[] args) {
        Platform.startup(MacLauncher::startGame);
    }

    private static void startGame() {
        Main game = new Main();
        Stage stage = new Stage();
        game.start(stage);
        quitWithoutToolkitShutdown(stage);
    }

    /**
     * Leaves the process as soon as the game is closed.
     *
     * <p>JavaFX's macOS shutdown clears the Glass event thread while AppKit is still delivering
     * window-close notifications, and those late callbacks then fail their event-thread check and
     * crash the process. That happens on the JVM too, so it cannot be fixed from here. Both quit
     * routes raise a close request first, though: the window's close button directly, and the macOS
     * Quit command through Glass, which asks every window to close. Everything the game persists is
     * written synchronously as it changes, and {@link Main} closes its LAN sockets in its own
     * handler, so there is nothing left to flush once that handler has run.
     */
    private static void quitWithoutToolkitShutdown(Stage stage) {
        EventHandler<WindowEvent> gameHandler = stage.getOnCloseRequest();
        stage.setOnCloseRequest(event -> {
            if (gameHandler != null) gameHandler.handle(event);
            event.consume();
            Runtime.getRuntime().halt(0);
        });
    }
}
