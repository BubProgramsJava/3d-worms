import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Drives the real UI so GraalVM's tracing agent observes every screen.
 *
 * <p>The agent only records what actually runs, so this walks the main menu, gameplay, pause menu,
 * duel, LAN discovery, customization shop, and settings remapping before quitting normally. Run it
 * with a throwaway {@code user.home} so the traced session never touches a real player profile.
 *
 * <p>Building this class as a second native image re-walks the same screens inside the executable,
 * which is how missing registrations are found without clicking through the game by hand.
 */
public final class TraceDriver {
    private static final long FX_TIMEOUT_SECONDS = 30;

    /** How the traced session ends; see {@link #quit()}. */
    private static volatile boolean quitByClosingWindow = true;

    private TraceDriver() {
    }

    public static void main(String[] args) throws Exception {
        for (String argument : args) {
            if (argument.equals("--quit=exit")) quitByClosingWindow = false;
            else if (argument.equals("--quit=close")) quitByClosingWindow = true;
            else throw new IllegalArgumentException("Unknown option: " + argument);
        }
        Thread driver = new Thread(TraceDriver::drive, "trace-driver");
        driver.setDaemon(true);
        driver.start();
        se.hirt.bubb.worm.MacLauncher.main(new String[0]);
    }

    private static void drive() {
        try {
            awaitStage();
            settle(2500);

            step("name the player", () -> find(TextField.class).setText("Tracer"));
            click("Claim Name", "Change Name");
            settle(600);

            step("settings", () -> fire("Settings"));
            settle(600);
            clickPrefix("Away");
            key(KeyCode.W);
            settle(400);
            key(KeyCode.ESCAPE);
            click("Back to Main Menu");
            settle(600);

            step("customize", () -> fire("Customize"));
            settle(1200);
            clickPrefix("Buy ");
            settle(400);
            clickPrefix("Use ", "Using ");
            settle(400);
            clickPrefix("No hat", "Remove hat");
            settle(400);
            click("Back to Main Menu");
            settle(600);

            step("duel", () -> fire("Duel"));
            settle(600);
            click("Start Duel");
            settle(1500);
            playRound();
            key(KeyCode.ESCAPE);
            settle(700);
            click("Return to Main Menu");
            settle(800);

            step("LAN discovery", () -> fire("LAN Multiplayer"));
            settle(1200);
            click("Refresh Players");
            settle(800);
            click("Host a Game");
            settle(2500);
            clickAny("Leave Lobby", "Back to Main Menu");
            settle(1000);

            step("single player", () -> fire("Play"));
            settle(1500);
            playRound();
            key(KeyCode.ESCAPE);
            settle(700);
            click("Resume");
            settle(800);
            key(KeyCode.ESCAPE);
            settle(700);
            click("Return to Main Menu");
            settle(1000);

            System.out.println("[trace] finished walking the UI, exiting");
        } catch (Throwable failure) {
            System.err.println("[trace] driver failed: " + failure);
            failure.printStackTrace();
        } finally {
            Platform.runLater(TraceDriver::quit);
        }
    }

    /**
     * Ends the session the way the chosen verification needs.
     *
     * <p>Closing the window is how a player quits, so that is the default and what the native
     * executable is checked against. The tracing agent writes its metadata from a JVM shutdown
     * hook, which the launcher's immediate exit on close would skip, so a tracing run passes
     * {@code --quit=exit} and accepts JavaFX's unclean toolkit shutdown instead.
     */
    private static void quit() {
        if (!quitByClosingWindow) {
            Platform.exit();
            return;
        }
        Window window = primaryScene().getWindow();
        Event.fireEvent(window, new WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    /** Plays a few turns and orbits the camera so gameplay and 3D rendering are traced. */
    private static void playRound() throws Exception {
        KeyCode[] turns = {KeyCode.UP, KeyCode.RIGHT, KeyCode.DOWN, KeyCode.LEFT, KeyCode.W};
        for (KeyCode turn : turns) {
            key(turn);
            settle(450);
        }
        key(KeyCode.C);
        settle(400);
        orbitCamera();
        settle(600);
        key(KeyCode.C);
        settle(400);
    }

    private static void orbitCamera() throws Exception {
        onFx(() -> {
            SubScene view = find(SubScene.class);
            mouse(view, MouseEvent.MOUSE_PRESSED, 420, 320, false);
            for (int offset = 10; offset <= 60; offset += 25) {
                mouse(view, MouseEvent.MOUSE_DRAGGED, 420 + offset, 320 + offset / 2, true);
            }
            mouse(view, MouseEvent.MOUSE_RELEASED, 480, 350, false);
            Event.fireEvent(view, new ScrollEvent(ScrollEvent.SCROLL, 420, 320, 420, 320,
                    false, false, false, false, false, false, 0, 40, 0, 40,
                    ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                    ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null));
            return null;
        });
    }

    private static void mouse(SubScene target, javafx.event.EventType<MouseEvent> type,
            double x, double y, boolean dragging) {
        Event.fireEvent(target, new MouseEvent(type, x, y, x, y, MouseButton.SECONDARY, 1,
                false, false, false, false, false, false, dragging, false, false, true, null));
    }

    // --- UI helpers -------------------------------------------------------

    private static void awaitStage() throws Exception {
        for (int attempt = 0; attempt < 240; attempt++) {
            Thread.sleep(250);
            try {
                Boolean ready = onFx(() -> {
                    for (Window window : Window.getWindows()) {
                        if (window.isShowing() && window.getScene() != null) return true;
                    }
                    return false;
                });
                if (Boolean.TRUE.equals(ready)) return;
            } catch (IllegalStateException toolkitNotUpYet) {
                // Platform.runLater throws until the FX toolkit has started.
            }
        }
        throw new IllegalStateException("The application window never appeared");
    }

    private static void step(String name, FxAction action) throws Exception {
        System.out.println("[trace] " + name);
        onFx(() -> {
            action.run();
            return null;
        });
    }

    private static void click(String... labels) throws Exception {
        step("click " + labels[0], () -> fire(labels));
    }

    private static void clickAny(String... labels) throws Exception {
        System.out.println("[trace] click any of " + String.join(", ", labels));
        onFx(() -> {
            for (String label : labels) {
                Button button = findButton(label, false);
                if (button != null) {
                    press(button);
                    return null;
                }
            }
            throw new IllegalStateException("No button matched " + String.join(", ", labels));
        });
    }

    private static void clickPrefix(String... prefixes) throws Exception {
        System.out.println("[trace] click prefix " + prefixes[0]);
        onFx(() -> {
            for (String prefix : prefixes) {
                Button button = findButton(prefix, true);
                if (button != null) {
                    press(button);
                    return null;
                }
            }
            throw new IllegalStateException("No button started with " + String.join(", ", prefixes));
        });
    }

    /** Fires the first button whose text matches one of the labels. Call on the FX thread. */
    private static void fire(String... labels) {
        for (String label : labels) {
            Button button = findButton(label, false);
            if (button != null) {
                press(button);
                return;
            }
        }
        throw new IllegalStateException("No button labelled " + String.join(", ", labels)
                + "; visible buttons: " + buttonLabels());
    }

    private static void press(Button button) {
        if (button.isDisabled()) {
            throw new IllegalStateException("Button is disabled: " + button.getText());
        }
        button.fire();
    }

    private static Button findButton(String label, boolean prefix) {
        for (Node node : visibleNodes()) {
            if (!(node instanceof Button button)) continue;
            String text = button.getText();
            if (text == null) continue;
            if (prefix ? text.startsWith(label) : text.equals(label)) return button;
        }
        return null;
    }

    private static List<String> buttonLabels() {
        List<String> labels = new ArrayList<>();
        for (Node node : visibleNodes()) {
            if (node instanceof Button button) labels.add(String.valueOf(button.getText()));
        }
        return labels;
    }

    private static <T> T find(Class<T> type) {
        for (Node node : visibleNodes()) {
            if (type.isInstance(node)) return type.cast(node);
        }
        throw new IllegalStateException("No " + type.getSimpleName() + " in the scene");
    }

    private static List<Node> visibleNodes() {
        List<Node> nodes = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene != null) collect(scene.getRoot(), nodes);
        }
        return nodes;
    }

    private static void collect(Node node, List<Node> nodes) {
        if (node == null || !node.isVisible()) return;
        nodes.add(node);
        if (node instanceof SubScene subScene) collect(subScene.getRoot(), nodes);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) collect(child, nodes);
        }
    }

    private static void key(KeyCode code) throws Exception {
        onFx(() -> {
            Scene scene = primaryScene();
            String character = code.getChar();
            Event.fireEvent(scene, new KeyEvent(KeyEvent.KEY_PRESSED, character, code.getName(),
                    code, false, false, false, false));
            Event.fireEvent(scene, new KeyEvent(KeyEvent.KEY_RELEASED, character, code.getName(),
                    code, false, false, false, false));
            return null;
        });
    }

    private static Scene primaryScene() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window.getScene() != null) return window.getScene();
        }
        throw new IllegalStateException("No showing window");
    }

    /** Lets animation pulses and background threads run between scripted actions. */
    private static void settle(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private interface FxAction {
        void run();
    }
}
