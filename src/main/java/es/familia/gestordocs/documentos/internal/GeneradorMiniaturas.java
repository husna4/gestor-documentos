package es.familia.gestordocs.documentos.internal;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.coobird.thumbnailator.Thumbnails;

/**
 * Genera una miniatura JPEG de como mucho {@value #LADO}x{@value #LADO} píxeles.
 * Thumbnailator gira la imagen según la orientación EXIF, así las fotos del móvil no salen tumbadas.
 */
@Component
public class GeneradorMiniaturas {

    static final int LADO = 400;

    private static final Logger log = LoggerFactory.getLogger(GeneradorMiniaturas.class);

    /** Vacío si la imagen no se puede leer: entonces se muestra un icono en su lugar. */
    public Optional<byte[]> generar(InputStream imagen) {
        try {
            var reducida = Thumbnails.of(imagen).size(LADO, LADO).asBufferedImage();
            // Fondo blanco: JPEG no tiene transparencia y los PNG transparentes saldrían negros.
            var rgb = new BufferedImage(reducida.getWidth(), reducida.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = rgb.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                g.drawImage(reducida, 0, 0, null);
            } finally {
                g.dispose();
            }
            var salida = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", salida);
            return Optional.of(salida.toByteArray());
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo generar la miniatura: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
