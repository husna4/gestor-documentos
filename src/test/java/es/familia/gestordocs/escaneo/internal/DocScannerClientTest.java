package es.familia.gestordocs.escaneo.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

import es.familia.gestordocs.Muestras;
import es.familia.gestordocs.escaneo.EscaneoException;

class DocScannerClientTest {
    private HttpServer servidor;
    private URI url;
    private byte[] jpeg;
    private final AtomicReference<Responder> respuesta = new AtomicReference<>();
    private final AtomicReference<byte[]> peticion = new AtomicReference<>();
    private final AtomicReference<String> tipoPeticion = new AtomicReference<>();
    private final ArrayList<DocScannerClient> clientes = new ArrayList<>();
    private final CountDownLatch liberar = new CountDownLatch(1);
    private final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @BeforeEach
    void preparar() throws IOException {
        jpeg = Muestras.jpeg("foto.jpg").contenido().getInputStream().readAllBytes();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(executor);
        url = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/api/scan");
        respuesta.set(e -> enviar(e, 200, jpeg, "image/jpeg", "detected", false));
        servidor.createContext("/api/scan", e -> {
            try (e) {
                peticion.set(e.getRequestBody().readAllBytes());
                tipoPeticion.set(e.getRequestHeaders().getFirst("Content-Type"));
                respuesta.get().enviar(e);
            }
        });
        servidor.start();
    }

    @AfterEach
    void cerrar() {
        liberar.countDown();
        clientes.forEach(DocScannerClient::cerrar);
        servidor.stop(0);
        executor.shutdownNow();
    }

    private DocScannerClient cliente(long maxBytes, Duration timeout) {
        var cliente = new DocScannerClient(new DocScannerProperties(url, Duration.ofMillis(300), timeout,
                DataSize.ofBytes(maxBytes)));
        clientes.add(cliente);
        return cliente;
    }

    private DocScannerClient cliente() {
        return cliente(2_000_000, Duration.ofSeconds(3));
    }

    @Test
    void enviaMultipartYDevuelveElJpegConEstadoDeDeteccion() {
        var resultado = cliente().escanear(new byte[]{1, 2, 3, 4}, "image/png");
        assertThat(resultado.contenido()).isEqualTo(jpeg);
        assertThat(resultado.documentoDetectado()).isTrue();
        assertThat(tipoPeticion.get()).startsWith("multipart/form-data; boundary=DocScanner-");
        assertThat(new String(peticion.get(), StandardCharsets.ISO_8859_1))
                .contains("name=\"file\"; filename=\"foto.png\"", "Content-Type: image/png",
                        "name=\"filter\"\r\n\r\nenhanced", "name=\"auto_crop\"\r\n\r\ntrue");
        assertThat(peticion.get()).containsSubsequence((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

    @Test
    void sinBordesConservaLaRespuestaEInformaDelFallback() {
        respuesta.set(e -> enviar(e, 200, jpeg, "image/jpeg; charset=binary", "not_detected", false));
        var resultado = cliente().escanear(jpeg, "image/jpeg");
        assertThat(resultado.documentoDetectado()).isFalse();
        assertThat(resultado.contenido()).isEqualTo(jpeg);
    }

    @ParameterizedTest
    @ValueSource(ints = {413, 415, 422, 500, 503})
    void erroresDelServidorSonRecuperablesYSinContenidoRemoto(int status) {
        respuesta.set(e -> enviar(e, status, "detalle-privado".getBytes(StandardCharsets.UTF_8),
                "application/json", "", false));
        assertThatThrownBy(() -> cliente().escanear(jpeg, "image/jpeg"))
                .isInstanceOf(EscaneoException.class)
                .hasMessageContaining("Escanear fotos")
                .hasMessageNotContaining("detalle-privado");
    }

    @Test
    void noSigueRedirecciones() {
        var redirecciones = new AtomicInteger();
        servidor.createContext("/destino", e -> {
            redirecciones.incrementAndGet();
            enviar(e, 200, jpeg, "image/jpeg", "detected", false);
            e.close();
        });
        respuesta.set(e -> {
            e.getResponseHeaders().set("Location", "/destino");
            e.sendResponseHeaders(302, -1);
        });
        assertThatThrownBy(() -> cliente().escanear(jpeg, "image/jpeg")).isInstanceOf(EscaneoException.class);
        assertThat(redirecciones.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void limitaRespuestasConLongitudYChunked(boolean chunked) {
        respuesta.set(e -> enviar(e, 200, new byte[4096], "image/jpeg", "detected", chunked));
        assertThatThrownBy(() -> cliente(256, Duration.ofSeconds(3)).escanear(jpeg, "image/jpeg"))
                .isInstanceOf(EscaneoException.class).hasMessageContaining("tamaño máximo");
    }

    @Test
    void rechazaUnMimeIncorrecto() {
        respuesta.set(e -> enviar(e, 200, jpeg, "text/html", "detected", false));
        assertThatThrownBy(() -> cliente().escanear(jpeg, "image/jpeg"))
                .isInstanceOf(EscaneoException.class).hasMessageContaining("respuesta no válida");
    }

    @Test
    void rechazaContenidoQueNoEsJpegAunqueLaCabeceraLoDiga() {
        respuesta.set(e -> enviar(e, 200, "esto-no-es-jpeg".getBytes(StandardCharsets.UTF_8),
                "image/jpeg", "detected", false));
        assertThatThrownBy(() -> cliente().escanear(jpeg, "image/jpeg"))
                .isInstanceOf(EscaneoException.class).hasMessageContaining("imagen no válida");
    }

    @Test
    void exigeLaCabeceraDeDeteccionDelContrato() {
        respuesta.set(e -> enviar(e, 200, jpeg, "image/jpeg", "", false));
        assertThatThrownBy(() -> cliente().escanear(jpeg, "image/jpeg"))
                .isInstanceOf(EscaneoException.class).hasMessageContaining("respuesta no válida");
    }

    @Test
    void elTimeoutIncluyeUnCuerpoQueNoTermina() {
        respuesta.set(e -> {
            e.getResponseHeaders().set("Content-Type", "image/jpeg");
            e.getResponseHeaders().set("X-Scan-Detection", "detected");
            e.sendResponseHeaders(200, 0);
            e.getResponseBody().write(1);
            e.getResponseBody().flush();
            try {
                liberar.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        var inicio = System.nanoTime();
        assertThatThrownBy(() -> cliente(100_000, Duration.ofMillis(250)).escanear(jpeg, "image/jpeg"))
                .isInstanceOf(EscaneoException.class).hasMessageContaining("tardado demasiado");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void servicioApagadoDaUnMensajeClaro() {
        servidor.stop(0);
        assertThatThrownBy(() -> cliente().escanear(jpeg, "image/jpeg"))
                .isInstanceOf(EscaneoException.class).hasMessageContaining("Comprueba que está iniciado");
    }

    @Test
    void tiposNoCompatiblesNoSeEnvian() {
        assertThatThrownBy(() -> cliente().escanear(jpeg, "image/gif"))
                .isInstanceOf(EscaneoException.class).hasMessageContaining("no admite escaneo");
        assertThat(peticion.get()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///tmp/scan", "http://user:password@localhost/api/scan", "http://localhost/#fragment"})
    void validaLaUrlDeConfiguracion(String urlInvalida) {
        assertThatThrownBy(() -> new DocScannerProperties(URI.create(urlInvalida), Duration.ofSeconds(1),
                Duration.ofSeconds(1), DataSize.ofMegabytes(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enviaElFiltroElegidoYPermiteDesactivarRecorte() {
        respuesta.set(e -> enviar(e, 200, jpeg, "image/jpeg", "disabled", false));
        var resultado = cliente().escanear(jpeg, "image/jpeg", es.familia.gestordocs.escaneo.FiltroEscaneo.bw, false);
        assertThat(resultado.contenido()).isEqualTo(jpeg);
        assertThat(new String(peticion.get(), StandardCharsets.ISO_8859_1))
                .contains("name=\"filter\"\r\n\r\nbw", "name=\"auto_crop\"\r\n\r\nfalse");
    }

    @Test
    void rechazaJpegTruncadoAunqueContengaDimensionesYMarcadorFinalFalso() {
        var truncado = java.util.Arrays.copyOf(jpeg, 700);
        truncado[698] = (byte) 0xff; truncado[699] = (byte) 0xd9;
        respuesta.set(e -> enviar(e, 200, truncado, "image/jpeg", "detected", false));
        assertThatThrownBy(() -> cliente().escanear(jpeg, "image/jpeg"))
                .isInstanceOf(EscaneoException.class);
    }

    private static void enviar(HttpExchange e, int status, byte[] body, String mime, String deteccion,
                               boolean chunked) throws IOException {
        e.getResponseHeaders().set("Content-Type", mime);
        if (!deteccion.isEmpty()) {
            e.getResponseHeaders().set("X-Scan-Detection", deteccion);
        }
        e.sendResponseHeaders(status, chunked ? 0 : body.length);
        e.getResponseBody().write(body);
    }

    @FunctionalInterface
    private interface Responder {
        void enviar(HttpExchange exchange) throws IOException;
    }
}
