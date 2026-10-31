package es.familia.gestordocs;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.springframework.core.io.ByteArrayResource;

import es.familia.gestordocs.documentos.FicheroSubido;

/** Ficheros de prueba generados en memoria. */
public final class Muestras {

    private Muestras() {
    }

    public static FicheroSubido jpeg(String nombre) {
        return fichero(nombre, imagen("jpg", 800, 600));
    }

    public static FicheroSubido png(String nombre) {
        return fichero(nombre, imagen("png", 300, 200));
    }

    /** Un PDF mínimo: lo que importa para detectar el tipo es la cabecera "%PDF-". */
    public static FicheroSubido pdf(String nombre) {
        return fichero(nombre, "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
    }

    public static FicheroSubido texto(String nombre) {
        return fichero(nombre, "Texto de prueba con tildes: María, José.".getBytes(StandardCharsets.UTF_8));
    }

    /** Un ejecutable de Windows (empieza por "MZ") con nombre de foto: debe rechazarse. */
    public static FicheroSubido ejecutableDisfrazado(String nombre) {
        var bytes = new byte[512];
        bytes[0] = 'M';
        bytes[1] = 'Z';
        bytes[0x3c] = (byte) 0x80;
        bytes[0x80] = 'P';
        bytes[0x81] = 'E';
        return fichero(nombre, bytes);
    }

    public static FicheroSubido vacio(String nombre) {
        return fichero(nombre, new byte[0]);
    }

    public static FicheroSubido deTamano(String nombre, int bytes) {
        var contenido = new byte[bytes];
        System.arraycopy("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII), 0, contenido, 0, 9);
        return fichero(nombre, contenido);
    }

    public static FicheroSubido fichero(String nombre, byte[] contenido) {
        return new FicheroSubido(nombre, contenido.length, new ByteArrayResource(contenido));
    }

    private static byte[] imagen(String formato, int ancho, int alto) {
        var imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        var g = imagen.createGraphics();
        g.setColor(new Color(30, 110, 200));
        g.fillRect(0, 0, ancho, alto);
        g.setColor(Color.WHITE);
        g.fillOval(ancho / 4, alto / 4, ancho / 2, alto / 2);
        g.dispose();
        try {
            var salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, formato, salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
