package es.familia.gestordocs.escaneo.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import es.familia.gestordocs.escaneo.EscaneoException;
import es.familia.gestordocs.escaneo.EscaneoService;
import es.familia.gestordocs.escaneo.ResultadoEscaneo;
import jakarta.annotation.PreDestroy;

@Service
@EnableConfigurationProperties(DocScannerProperties.class)
public class DocScannerClient implements EscaneoService {
    private static final Map<String, String> EXTENSIONES = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp",
            "image/heic", "heic", "image/heif", "heif");
    private static final String RECUPERACION = " Reintenta o desmarca «Escanear fotos» para guardar el original.";
    private final DocScannerProperties propiedades;
    private final HttpClient cliente;

    public DocScannerClient(DocScannerProperties propiedades) {
        this.propiedades = propiedades;
        // No se envían fotos a un proxy del sistema ni se siguen redirecciones del servicio.
        cliente = HttpClient.newBuilder()
                .connectTimeout(propiedades.tiempoConexion())
                .proxy(ProxySelector.of(null))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public ResultadoEscaneo escanear(byte[] imagen, String tipoMime) {
        var extension = EXTENSIONES.get(tipoMime);
        if (extension == null) {
            throw new EscaneoException("El formato de esta imagen no admite escaneo automático.");
        }
        var boundary = "DocScanner-" + UUID.randomUUID();
        // Nombre fijo: el servicio de imagen no necesita nombres ni rutas personales.
        var principio = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"foto." + extension + "\"\r\n"
                + "Content-Type: " + tipoMime + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        var finalParte = ("\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"filter\"\r\n\r\nenhanced\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"auto_crop\"\r\n\r\ntrue\r\n"
                + "--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        var peticion = HttpRequest.newBuilder(propiedades.url())
                .timeout(propiedades.tiempoRespuesta())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "image/jpeg")
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(principio, imagen, finalParte)))
                .build();
        var intercambio = cliente.sendAsync(peticion,
                info -> new RespuestaLimitada(propiedades.tamanoMaximoRespuesta().toBytes()));
        try {
            // El plazo abarca cabeceras Y cuerpo, también si el servidor deja el cuerpo a medias.
            var respuesta = intercambio.get(propiedades.tiempoRespuesta().toMillis(), TimeUnit.MILLISECONDS);
            return validar(respuesta);
        } catch (TimeoutException e) {
            intercambio.cancel(true);
            throw new EscaneoException("El escáner ha tardado demasiado." + RECUPERACION);
        } catch (InterruptedException e) {
            intercambio.cancel(true);
            Thread.currentThread().interrupt();
            throw new EscaneoException("Se ha interrumpido el escaneo. Vuelve a intentarlo.");
        } catch (ExecutionException e) {
            var causa = e.getCause();
            while (causa.getCause() != null) {
                causa = causa.getCause();
            }
            if (causa instanceof RespuestaLimitada.RespuestaDemasiadoGrande) {
                throw new EscaneoException("La imagen procesada supera el tamaño máximo de respuesta.");
            }
            throw new EscaneoException("No se ha podido contactar con el escáner. Comprueba que está iniciado."
                    + RECUPERACION);
        }
    }

    private ResultadoEscaneo validar(HttpResponse<byte[]> respuesta) {
        if (respuesta.statusCode() != 200) {
            var mensaje = switch (respuesta.statusCode()) {
                case 413 -> "El escáner ha rechazado la foto por su tamaño o sus dimensiones.";
                case 415, 422 -> "El escáner no ha podido procesar esta foto.";
                case 503 -> "El escáner está ocupado.";
                default -> "El servicio de escaneo no está disponible.";
            };
            throw new EscaneoException(mensaje + RECUPERACION);
        }
        var tipo = respuesta.headers().firstValue("Content-Type").orElse("").split(";")[0].trim();
        var deteccion = respuesta.headers().firstValue("X-Scan-Detection").orElse("");
        if (!"image/jpeg".equalsIgnoreCase(tipo)
                || !("detected".equals(deteccion) || "not_detected".equals(deteccion))) {
            throw new EscaneoException("El escáner ha devuelto una respuesta no válida." + RECUPERACION);
        }
        var contenido = respuesta.body();
        // Validar formato y dimensiones sin cargar una imagen entera ni crear cachés en disco.
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(contenido))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Sin lector de imagen");
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                var ancho = reader.getWidth(0);
                var alto = reader.getHeight(0);
                if (!"JPEG".equalsIgnoreCase(reader.getFormatName()) || ancho < 32 || alto < 32
                        || (long) ancho * alto > 40_000_000) {
                    throw new IOException("Dimensiones o formato de imagen no válidos");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new EscaneoException("El escáner ha devuelto una imagen no válida." + RECUPERACION);
        }
        return new ResultadoEscaneo(contenido, "detected".equals(deteccion));
    }

    @PreDestroy
    public void cerrar() {
        cliente.shutdownNow();
    }
}
