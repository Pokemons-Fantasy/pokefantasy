package com.villu.pokefantasy.commands.users.avatar;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Qué foto de perfil se acepta. El cliente ya la recorta y la reduce (JPEG de 256x256); aquí solo se
 * comprueba que es un JPEG de verdad (por su contenido, no por la cabecera HTTP) y que no es mayor de
 * lo esperado. Solo se leen las cabeceras de la imagen, no se decodifica entera.
 */
public final class AvatarImagePolicy {

    public static final int MAX_BYTES = 300 * 1024;
    public static final int MAX_SIDE = 512;
    public static final String CONTENT_TYPE = "image/jpeg";

    private AvatarImagePolicy() {}

    public static void validate(byte[] image) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("La imagen está vacía");
        }
        if (image.length > MAX_BYTES) {
            throw new IllegalArgumentException("La imagen no puede superar " + MAX_BYTES / 1024 + " KB");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("El fichero no es una imagen");
            }
            ImageReader reader = readers.next();
            try {
                if (!"jpeg".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IllegalArgumentException("La imagen debe ser JPEG");
                }
                reader.setInput(in);
                if (reader.getWidth(0) > MAX_SIDE || reader.getHeight(0) > MAX_SIDE) {
                    throw new IllegalArgumentException(
                            "La imagen no puede medir más de " + MAX_SIDE + "x" + MAX_SIDE + " px");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("La imagen no se puede leer", e);
        }
    }
}
