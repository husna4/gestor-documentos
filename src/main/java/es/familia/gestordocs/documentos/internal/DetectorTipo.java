package es.familia.gestordocs.documentos.internal;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Averigua el tipo real de un fichero mirando sus primeros bytes ("magic numbers"), con la
 * extensión solo como pista para afinar (por ejemplo, un .docx es por dentro un ZIP).
 * Así un "foto.jpg" que en realidad es un ejecutable se detecta y se rechaza.
 */
@Component
public class DetectorTipo {

    private final Tika tika = new Tika();

    public String detectar(InputStream contenido, String nombreFichero) throws IOException {
        return tika.detect(contenido, nombreFichero);
    }
}
