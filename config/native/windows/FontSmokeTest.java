import javafx.scene.text.Font;
import javafx.scene.text.Text;

/** Native Image regression check for Windows font enumeration and fallback JNI. */
public final class FontSmokeTest {
    public static void main(String[] args) {
        System.out.println("Enumerating Windows fonts...");
        var families = Font.getFamilies();
        var names = Font.getFontNames();
        if (families.isEmpty() || names.isEmpty()) {
            throw new AssertionError("Windows font enumeration returned no fonts");
        }
        System.out.printf("Found %d families and %d font names.%n", families.size(), names.size());

        // These scripts exercise system-font fallback without opening a window.
        String[] samples = {
            "Multiplayer: Host / Join",
            "\u00c5\u00e4\u00f6 \u0391\u03bb\u03c6\u03b1 \u0418\u0433\u0440\u0430",
            "\u4f60\u597d \u65e5\u672c\u8a9e \ud55c\uad6d\uc5b4",
            "\u0627\u0644\u0644\u0639\u0628 \u0916\u0947\u0932",
            "\u2190 \u2192 \u2605 \ud83d\udc1b"
        };
        for (String sample : samples) {
            Text text = new Text(sample);
            text.setFont(Font.font("System", 18));
            double width = text.getLayoutBounds().getWidth();
            double height = text.getLayoutBounds().getHeight();
            if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0) {
                throw new AssertionError("Invalid text bounds for sample: " + sample);
            }
        }
        System.out.printf("Laid out %d fallback samples successfully.%n", samples.length);
    }
}
