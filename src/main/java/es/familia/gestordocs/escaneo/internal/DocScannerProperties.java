package es.familia.gestordocs.escaneo.internal;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** La URL es configuración del servidor; nunca procede de un formulario. */
@ConfigurationProperties("app.docscanner")
public record DocScannerProperties(URI url, Duration tiempoConexion, Duration tiempoRespuesta,
                                   DataSize tamanoMaximoRespuesta) {
    public DocScannerProperties {
        if (url == null || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                || url.getHost() == null || url.getUserInfo() != null
                || url.getFragment() != null || url.getQuery() != null) {
            throw new IllegalArgumentException("app.docscanner.url debe ser una URL HTTP(S) sin credenciales");
        }
        if (tiempoConexion == null || tiempoRespuesta == null
                || tiempoConexion.toMillis() < 1 || tiempoRespuesta.toMillis() < 1
                || tiempoConexion.compareTo(Duration.ofMinutes(5)) > 0
                || tiempoRespuesta.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Los tiempos de DocScanner deben estar entre 1 ms y 5 minutos");
        }
        if (tamanoMaximoRespuesta == null || tamanoMaximoRespuesta.toBytes() < 1
                || tamanoMaximoRespuesta.toBytes() > DataSize.ofMegabytes(100).toBytes()) {
            throw new IllegalArgumentException("El límite de respuesta de DocScanner debe estar entre 1 byte y 100 MB");
        }
    }
}
