package main.java.chess.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Loads the unchanged master logo from the application classpath, including JAR launches. */
final class ApplicationLogo {
    private static final BufferedImage IMAGE = load();

    private ApplicationLogo() { }

    static BufferedImage image() {
        return IMAGE;
    }

    private static BufferedImage load() {
        var resource = ApplicationLogo.class.getResource("/logo/chess-engine-logo.png");
        if (resource == null) {
            throw new IllegalStateException("Missing packaged Chess Engine logo");
        }
        try {
            var image = ImageIO.read(resource);
            if (image == null) {
                throw new IOException("Unsupported logo image format");
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load Chess Engine logo", exception);
        }
    }
}
