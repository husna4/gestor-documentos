package es.familia.gestordocs.documentos;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import es.familia.gestordocs.compartido.NoEncontradoException;
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.documentos.internal.AlmacenBorradores;
import es.familia.gestordocs.documentos.internal.BorradoresProperties;
import es.familia.gestordocs.documentos.internal.DetectorTipo;
import es.familia.gestordocs.documentos.internal.DocumentosProperties;
import es.familia.gestordocs.escaneo.EscaneoService;
import es.familia.gestordocs.escaneo.FiltroEscaneo;
import es.familia.gestordocs.usuarios.UsuarioService;

/** Borradores de una instancia: propietario, caducidad, cuotas y variantes calculadas bajo demanda. */
@Service
@EnableScheduling
@EnableConfigurationProperties(BorradoresProperties.class)
public class BorradorEscaneoService {
    private static final Logger log = LoggerFactory.getLogger(BorradorEscaneoService.class);
    private final Map<UUID, Borrador> borradores = new HashMap<>();
    private final AlmacenBorradores almacen;
    private final BorradoresProperties propiedades;
    private final DocumentosProperties documentos;
    private final EscaneoService escaneo;
    private final DetectorTipo detector;
    private final UsuarioService usuarios;
    private final Clock reloj;

    @org.springframework.beans.factory.annotation.Autowired
    public BorradorEscaneoService(AlmacenBorradores almacen, BorradoresProperties propiedades,
            DocumentosProperties documentos, EscaneoService escaneo, DetectorTipo detector, UsuarioService usuarios) {
        this(almacen, propiedades, documentos, escaneo, detector, usuarios, Clock.systemUTC());
    }

    BorradorEscaneoService(AlmacenBorradores almacen, BorradoresProperties propiedades,
            DocumentosProperties documentos, EscaneoService escaneo, DetectorTipo detector,
            UsuarioService usuarios, Clock reloj) {
        this.almacen = almacen; this.propiedades = propiedades; this.documentos = documentos;
        this.escaneo = escaneo; this.detector = detector; this.usuarios = usuarios; this.reloj = reloj;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public synchronized InfoBorrador crear(FicheroSubido fichero) {
        limpiarCaducados();
        var propietario = propietarioActual();
        if (borradores.size() >= propiedades.maximoTotal() || borradores.values().stream()
                .filter(b -> b.propietario.equals(propietario)).count() >= propiedades.maximoPorUsuario()) {
            throw new ReglaNegocioException("escaneo.demasiadas");
        }
        var limite = documentos.tamanoMaximo().toBytes();
        if (fichero.tamano() <= 0 || fichero.tamano() > limite) {
            throw new ReglaNegocioException("escaneo.fotoTamano");
        }
        var id = UUID.randomUUID();
        try (var input = fichero.contenido().getInputStream()) {
            var original = input.readNBytes((int) Math.min(limite, Integer.MAX_VALUE - 1L) + 1);
            if (original.length == 0 || original.length > limite) {
                throw new ReglaNegocioException("escaneo.fotoTamano");
            }
            var nombre = NombresFichero.limpiar(fichero.nombreOriginal());
            var tipo = detector.detectar(new java.io.ByteArrayInputStream(original), nombre);
            if (!TiposFichero.esEscaneable(tipo)) {
                throw new ReglaNegocioException("escaneo.incompatible");
            }
            comprobarEspacio(original.length);
            almacen.escribir(id, "fuente", original);
            var borrador = new Borrador(id, propietario, nombre, tipo, reloj.instant());
            borradores.put(id, borrador);
            tocar(borrador);
            return new InfoBorrador(id, nombre, tipo);
        } catch (IOException e) {
            borradores.remove(id);
            try { almacen.borrar(id); } catch (IOException ignored) { log.warn("Limpieza temporal pendiente"); }
            throw errorDisco(e);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public synchronized VistaBorrador filtrar(UUID id, String variante) {
        validarVariante(variante);
        var b = buscar(id);
        comprobarDisponible(b);
        tocar(b);
        if (variante.equals("fuente")) { return new VistaBorrador("fuente", false); }
        var filtro = FiltroEscaneo.valueOf(variante);
        if (!b.variantes.containsKey(filtro)) {
            try {
                var original = almacen.leer(id, "fuente");
                var resultado = escaneo.escanear(original, b.tipo, filtro, true);
                var sinBordes = !resultado.documentoDetectado();
                // DocScanner no filtra cuando falla la detección: pedir explícitamente la imagen completa.
                if (sinBordes && filtro != FiltroEscaneo.original) {
                    resultado = escaneo.escanear(original, b.tipo, filtro, false);
                }
                var contenido = resultado.contenido();
                if (contenido.length == 0 || contenido.length > documentos.tamanoMaximo().toBytes()) {
                    throw new ReglaNegocioException("escaneo.procesadaTamano");
                }
                comprobarEspacio(contenido.length);
                almacen.escribir(id, variante, contenido);
                b.variantes.put(filtro, sinBordes);
                tocar(b);
            } catch (IOException e) { throw errorDisco(e); }
        }
        return new VistaBorrador(variante, b.variantes.get(filtro));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public synchronized ImagenBorrador imagen(UUID id, String variante) {
        validarVariante(variante);
        var b = buscar(id);
        exigirVariante(b, variante);
        tocar(b);
        try {
            return new ImagenBorrador(almacen.leer(id, variante), variante.equals("fuente") ? b.tipo : "image/jpeg");
        } catch (IOException e) { throw errorDisco(e); }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public synchronized void descartar(UUID id) {
        var b = buscar(id);
        comprobarDisponible(b);
        eliminar(b);
    }

    /** Bloquea la foto hasta el final de la transacción: no puede guardarse dos veces en paralelo. */
    @PreAuthorize("hasRole('ADMIN')")
    public synchronized ReservaBorrador reservar(SeleccionBorrador seleccion) {
        validarVariante(seleccion.variante());
        var b = buscar(seleccion.id());
        comprobarDisponible(b);
        exigirVariante(b, seleccion.variante());
        tocar(b);
        try {
            var bytes = almacen.leer(b.id, seleccion.variante());
            var fuente = seleccion.variante().equals("fuente");
            var base = NombresFichero.sinExtension(b.nombre);
            var nombre = fuente ? b.nombre : base.substring(0, Math.min(base.length(), 251)) + ".jpg";
            var token = UUID.randomUUID();
            b.reserva = token;
            return new ReservaBorrador(b.id, token,
                    new FicheroSubido(nombre, bytes.length, new ByteArrayResource(bytes)));
        } catch (IOException e) { throw errorDisco(e); }
    }

    /** Solo para la sincronización transaccional del módulo; no se expone mediante HTTP. */
    public synchronized void terminarReserva(ReservaBorrador reserva, boolean confirmado) {
        var b = borradores.get(reserva.id());
        if (b == null || !reserva.token().equals(b.reserva)) { return; }
        b.reserva = null;
        if (confirmado) { eliminar(b); }
        else { tocar(b); }
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 0)
    synchronized void limpiarCaducados() {
        var limite = reloj.instant().minus(propiedades.caducidad());
        for (var b : borradores.values().toArray(Borrador[]::new)) {
            if (b.reserva == null && (b.descartado || !b.actividad.isAfter(limite))) { eliminar(b); }
        }
        try { almacen.limpiarHuerfanos(limite, Set.copyOf(borradores.keySet())); }
        catch (IOException e) { log.warn("Limpieza de borradores temporales pendiente"); }
    }

    private Borrador buscar(UUID id) {
        var b = borradores.get(id);
        if (b == null || b.descartado || !b.propietario.equals(propietarioActual())) { throw noExiste(); }
        if (b.reserva == null && !b.actividad.plus(propiedades.caducidad()).isAfter(reloj.instant())) {
            eliminar(b); throw noExiste();
        }
        return b;
    }

    private void exigirVariante(Borrador b, String variante) {
        if (!variante.equals("fuente") && !b.variantes.containsKey(FiltroEscaneo.valueOf(variante))) {
            throw new ReglaNegocioException("escaneo.previsualizar");
        }
    }

    private static void validarVariante(String variante) {
        if (variante == null || !Set.of("fuente", "original", "grayscale", "bw", "enhanced").contains(variante)) {
            throw new ReglaNegocioException("escaneo.filtroInvalido");
        }
    }

    private static void comprobarDisponible(Borrador b) {
        if (b.reserva != null) { throw new ReglaNegocioException("escaneo.guardando"); }
    }

    private Long propietarioActual() {
        return usuarios.idUsuarioActual().orElseThrow(BorradorEscaneoService::noExiste);
    }

    private void comprobarEspacio(long nuevosBytes) throws IOException {
        if (nuevosBytes > propiedades.espacioMaximo().toBytes() - almacen.espacioUtilizado()) {
            throw new ReglaNegocioException("escaneo.sinEspacio");
        }
    }

    private void tocar(Borrador b) {
        b.actividad = reloj.instant();
        try { almacen.tocar(b.id, b.actividad); } catch (IOException e) { throw errorDisco(e); }
    }

    private void eliminar(Borrador b) {
        b.descartado = true;
        try { almacen.borrar(b.id); borradores.remove(b.id); }
        catch (IOException e) { log.warn("Limpieza de un borrador temporal pendiente; se reintentará"); }
    }

    private static NoEncontradoException noExiste() {
        return new NoEncontradoException("escaneo.caducado");
    }
    private static ReglaNegocioException errorDisco(IOException causa) {
        log.error("Fallo del almacenamiento temporal", causa);
        return new ReglaNegocioException("escaneo.errorDisco", causa);
    }

    private static class Borrador {
        final UUID id; final Long propietario; final String nombre; final String tipo;
        final Map<FiltroEscaneo, Boolean> variantes = new EnumMap<>(FiltroEscaneo.class);
        Instant actividad; UUID reserva; boolean descartado;
        Borrador(UUID id, Long propietario, String nombre, String tipo, Instant actividad) {
            this.id = id; this.propietario = propietario; this.nombre = nombre; this.tipo = tipo; this.actividad = actividad;
        }
    }

    public record InfoBorrador(UUID id, String nombre, String tipo) { }
    public record VistaBorrador(String variante, boolean sinBordes) { }
    public record ImagenBorrador(byte[] contenido, String tipo) { }
    public record ReservaBorrador(UUID id, UUID token, FicheroSubido fichero) { }
}
