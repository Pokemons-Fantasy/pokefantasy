package com.villu.pokefantasy.commands.users.avatar;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Imágenes reales generadas en memoria para los tests del avatar. */
public final class TestImages {

    private TestImages() {}

    public static byte[] jpeg(int width, int height) {
        return encode(width, height, "jpg");
    }

    public static byte[] png(int width, int height) {
        return encode(width, height, "png");
    }

    private static byte[] encode(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
