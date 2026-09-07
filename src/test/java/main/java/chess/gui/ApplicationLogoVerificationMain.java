package main.java.chess.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import main.java.chess.model.FenCodec;

/** Run with the packaged JAR and test classes on the classpath in a desktop session. */
public final class ApplicationLogoVerificationMain {
    public static void main(String[] args) throws Exception {
        try {
            var resource = ChessWindow.class.getResource("/logo/chess-engine-logo.png");
            if (resource == null || !"jar".equals(resource.getProtocol())) {
                throw new AssertionError("Logo must load from the packaged JAR: " + resource);
            }
            try (var stream = resource.openStream()) {
                if (!Arrays.equals(stream.readAllBytes(), Files.readAllBytes(Path.of(args[0])))) {
                    throw new AssertionError("Packaged PNG differs from the master");
                }
            }
            SwingUtilities.invokeAndWait(() -> {
                var window = new ChessWindow(FenCodec.parse(
                        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
                try {
                    if (window.getIconImages().size() != 1
                            || window.getIconImages().getFirst() != ApplicationLogo.image()) {
                        throw new AssertionError("Primary window did not receive the master logo");
                    }
                    window.setVisible(true);
                    if (!window.isShowing()) throw new AssertionError("Window did not open");
                    System.out.println("PASS: packaged PNG unchanged; primary window opens with logo: " + resource);
                } finally {
                    window.dispose();
                }
            });
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
