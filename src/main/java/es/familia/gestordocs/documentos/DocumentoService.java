package es.familia.gestordocs.documentos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import es.familia.gestordocs.almacenamiento.StorageService;
import es.familia.gestordocs.compartido.ErrorAplicacion;
import es.familia.gestordocs.compartido.Mensaje;
import es.familia.gestordocs.compartido.NoEncontradoException;
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.documentos.internal.DetectorTipo;
import es.familia.gestordocs.documentos.internal.Documento;
import es.familia.gestordocs.documentos.internal.DocumentoRepository;
import es.familia.gestordocs.documentos.internal.DocumentosProperties;
import es.familia.gestordocs.documentos.internal.GeneradorMiniaturas;
import es.familia.gestordocs.escaneo.EscaneoException;
import es.familia.gestordocs.escaneo.EscaneoService;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionDto;
import es.familia.gestordocs.secciones.SeccionService;
import es.familia.gestordocs.usuarios.UsuarioService;

/**
 * API pública del módulo documentos. Ver y descargar: cualquier usuario con sesión.
 * Subir, editar y borrar: solo ADMIN.
 */
@Service
@Transactional(readOnly = true)
@EnableConfigurationProperties(DocumentosProperties.class)
@PreAuthorize("isAuthenticated()")
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private static final String CARPETA_FICHEROS = "ficheros/";
    private static final String CARPETA_MINIATURAS = "miniaturas/";

    private final DocumentoRepository documentos;
    private final StorageService storage;
    private final DetectorTipo detectorTipo;
    private final GeneradorMiniaturas miniaturas;
    private final PersonaService personaService;
    private final SeccionService seccionService;
    private final UsuarioService usuarioService;
    private final DocumentosProperties propiedades;
    private final EscaneoService escaneo;
    private final BorradorEscaneoService borradores;

    DocumentoService(DocumentoRepository documentos, StorageService storage, DetectorTipo detectorTipo,
            GeneradorMiniaturas miniaturas, PersonaService personaService, SeccionService seccionService,
            UsuarioService usuarioService, DocumentosProperties propiedades, EscaneoService escaneo,
            BorradorEscaneoService borradores) {
        this.documentos = documentos;
        this.storage = storage;
        this.detectorTipo = detectorTipo;
        this.miniaturas = miniaturas;
        this.personaService = personaService;
        this.seccionService = seccionService;
        this.usuarioService = usuarioService;
        this.propiedades = propiedades;
        this.escaneo = escaneo;
        this.borradores = borradores;
    }

    // ---- Consultas ----

    public DocumentoDto obtener(Long id) {
        return aDto(buscarEntidad(id));
    }

    public List<DocumentoDto> recientes(int cuantos) {
        return aDtos(documentos.findByOrderBySubidoEnDesc(Limit.of(cuantos)));
    }

    /** @param personaId filtro opcional (null = todas las personas) */
    public List<DocumentoDto> porSeccion(Long seccionId, Long personaId) {
        return aDtos(personaId == null
                ? documentos.buscarPorSeccion(seccionId)
                : documentos.buscarPorSeccionYPersona(seccionId, personaId));
    }

    public List<DocumentoDto> sinClasificar() {
        return aDtos(documentos.buscarSinClasificar());
    }

    public List<DocumentoDto> porPersona(Long personaId) {
        return aDtos(documentos.findByPersonaIdOrderByNombreVisibleAsc(personaId));
    }

    /** Búsqueda por nombre, sin distinguir mayúsculas ni tildes. Máximo 50 resultados. */
    public List<DocumentoDto> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return aDtos(documentos.buscarPorNombre(escaparLike(texto.trim())));
    }

    public long contarPorPersona(Long personaId) {
        return documentos.countByPersonaId(personaId);
    }

    public long contarPorSeccion(Long seccionId) {
        return documentos.contarPorSeccion(seccionId);
    }

    /** Número de documentos de cada sección (las secciones vacías no aparecen). */
    public Map<Long, Long> contarPorCadaSeccion() {
        return documentos.contarPorCadaSeccion().stream()
                .collect(Collectors.toMap(fila -> (Long) fila[0], fila -> (Long) fila[1]));
    }

    public long contarSinClasificar() {
        return documentos.contarSinClasificar();
    }

    // ---- Ficheros ----

    public ContenidoDocumento contenido(Long id) {
        var documento = buscarEntidad(id);
        return new ContenidoDocumento(
                storage.leer(CARPETA_FICHEROS + documento.getClaveFichero()),
                NombresFichero.paraDescarga(documento.getNombreVisible(), documento.getNombreOriginal()),
                documento.getTipoMime(),
                documento.getTamanoBytes(),
                TiposFichero.esPrevisualizable(documento.getTipoMime()));
    }

    public Optional<Resource> miniatura(Long id) {
        var documento = buscarEntidad(id);
        return Optional.ofNullable(documento.getClaveMiniatura())
                .map(clave -> storage.leer(CARPETA_MINIATURAS + clave));
    }

    // ---- Administración ----

    /**
     * Sube varios ficheros con los mismos datos (secciones, persona, descripción).
     * Los ficheros que no pasan la validación se rechazan uno a uno sin impedir que se suban los demás.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResultadoSubida subir(SubidaForm datos, List<FicheroSubido> ficheros) {
        return subir(datos, ficheros, List.of());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResultadoSubida subir(SubidaForm datos, List<FicheroSubido> ficheros,
                                 List<SeleccionBorrador> selecciones) {
        if (ficheros.isEmpty() && selecciones.isEmpty()) {
            throw new ReglaNegocioException("documentos.elegirFichero");
        }
        if (selecciones.size() > 20 || selecciones.stream().map(SeleccionBorrador::id).distinct().count()
                != selecciones.size()) {
            throw new ReglaNegocioException("escaneo.seleccionInvalida");
        }
        validarSeccionesYPersona(datos.seccionIds(), datos.personaId());
        var subidoPor = usuarioService.idUsuarioActual()
                .orElseThrow(() -> new IllegalStateException("No hay un usuario con sesión iniciada"));

        var guardados = new ArrayList<Documento>();
        var errores = new ArrayList<Mensaje>();
        var avisos = new ArrayList<Mensaje>();
        for (var fichero : ficheros) {
            var nombre = NombresFichero.limpiar(fichero.nombreOriginal());
            var error = validarFichero(fichero);
            if (error.isPresent()) {
                errores.add(Mensaje.de("documentos.errorFichero", nombre, error.get()));
                continue;
            }
            try {
                var preparado = prepararFichero(fichero, nombre, datos.escanearFotos());
                guardados.add(guardarFichero(preparado.fichero(), preparado.nombre(), preparado.tipo(),
                        datos, subidoPor));
                if (preparado.sinBordes()) {
                    avisos.add(Mensaje.de("documentos.sinBordes", nombre));
                }
            } catch (EscaneoException e) {
                log.warn("No se pudo escanear el fichero {}", nombre, e);
                errores.add(Mensaje.de("documentos.errorFichero", nombre, e.getMensaje()));
            } catch (TipoNoPermitido e) {
                errores.add(Mensaje.de("documentos.errorFichero", nombre, e.getMensaje()));
            } catch (IOException e) {
                log.error("Error leyendo el fichero subido {}", nombre, e);
                errores.add(Mensaje.de("documentos.errorFichero", nombre, Mensaje.de("documentos.lectura")));
            }
        }
        for (var seleccion : selecciones) {
            BorradorEscaneoService.ReservaBorrador reserva = null;
            boolean sincronizada = false;
            try {
                reserva = borradores.reservar(seleccion);
                var fichero = reserva.fichero();
                var nombre = NombresFichero.limpiar(fichero.nombreOriginal());
                var error = validarFichero(fichero);
                if (error.isPresent()) { throw new ReglaNegocioException(error.get()); }
                // Ya se ha previsualizado: guardar sus bytes sin volver a escanear.
                var preparado = prepararFichero(fichero, nombre, false);
                guardados.add(guardarFichero(preparado.fichero(), preparado.nombre(), preparado.tipo(), datos, subidoPor));
                var pendiente = reserva;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int estado) {
                        borradores.terminarReserva(pendiente, estado == STATUS_COMMITTED);
                    }
                });
                sincronizada = true;
            } catch (es.familia.gestordocs.compartido.NoEncontradoException | ReglaNegocioException
                    | TipoNoPermitido e) {
                errores.add(Mensaje.de("documentos.errorPendiente", e.getMensaje()));
            } catch (IOException e) {
                errores.add(Mensaje.de("documentos.errorPendiente", Mensaje.de("documentos.lectura")));
            } finally {
                if (reserva != null && !sincronizada) { borradores.terminarReserva(reserva, false); }
            }
        }
        return new ResultadoSubida(aDtos(guardados), errores, avisos);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DocumentoDto editar(Long id, EdicionDocumentoForm datos) {
        validarSeccionesYPersona(datos.seccionIds(), datos.personaId());
        var documento = buscarEntidad(id);
        documento.actualizar(datos.nombreVisible(), datos.descripcion(), datos.personaId(), datos.seccionIds());
        return aDto(documento);
    }

    /** Borra el documento. El fichero del disco se borra solo cuando la transacción se confirma. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void eliminar(Long id) {
        var documento = buscarEntidad(id);
        documentos.delete(documento);
        var claves = new ArrayList<String>();
        claves.add(CARPETA_FICHEROS + documento.getClaveFichero());
        if (documento.getClaveMiniatura() != null) {
            claves.add(CARPETA_MINIATURAS + documento.getClaveMiniatura());
        }
        alTerminarTransaccion(true, claves);
    }

    // ---- Auxiliares ----

    /** Vacío si el fichero es válido; si no, el motivo del rechazo. */
    private Optional<Mensaje> validarFichero(FicheroSubido fichero) {
        if (fichero.tamano() == 0) {
            return Optional.of(Mensaje.de("documentos.vacio"));
        }
        if (fichero.tamano() > propiedades.tamanoMaximo().toBytes()) {
            return Optional.of(Mensaje.de("documentos.grande", tamanoMaximoLegible()));
        }
        return Optional.empty();
    }

    /** Valida antes de llamar al escáner y antes de crear ningún archivo persistente. */
    private FicheroPreparado prepararFichero(FicheroSubido fichero, String nombre, boolean escanearFotos)
            throws IOException {
        String tipo;
        try (var entrada = fichero.contenido().getInputStream()) {
            tipo = detectorTipo.detectar(entrada, nombre);
        }
        if (!TiposFichero.esAceptado(tipo)) {
            throw new TipoNoPermitido(tipo);
        }

        if (!escanearFotos || !TiposFichero.esEscaneable(tipo)) {
            return new FicheroPreparado(fichero, nombre, tipo, false);
        }
        byte[] original;
        var limite = propiedades.tamanoMaximo().toBytes();
        try (var entrada = fichero.contenido().getInputStream()) {
            original = entrada.readNBytes((int) Math.min(limite, Integer.MAX_VALUE - 1L) + 1);
        }
        if (original.length > limite) {
            throw new EscaneoException("escaneo.fotoGrande");
        }
        var resultado = escaneo.escanear(original, tipo);
        var procesado = resultado.contenido();
        if (procesado.length == 0 || procesado.length > limite) {
            throw new EscaneoException("escaneo.procesadaGrande");
        }
        var base = NombresFichero.sinExtension(nombre);
        var nombreJpeg = base.substring(0, Math.min(base.length(), 251)) + ".jpg";
        var foto = new FicheroSubido(nombreJpeg, procesado.length, new ByteArrayResource(procesado));
        return new FicheroPreparado(foto, nombreJpeg, "image/jpeg", !resultado.documentoDetectado());
    }

    private record FicheroPreparado(FicheroSubido fichero, String nombre, String tipo, boolean sinBordes) {
    }

    private Documento guardarFichero(FicheroSubido fichero, String nombre, String tipo,
                                    SubidaForm datos, Long subidoPor) throws IOException {
        var claveFichero = UUID.randomUUID();
        try (var entrada = fichero.contenido().getInputStream()) {
            guardarEnStorage(CARPETA_FICHEROS + claveFichero, entrada);
        }

        UUID claveMiniatura = null;
        if (TiposFichero.admiteMiniatura(tipo)) {
            try (var entrada = fichero.contenido().getInputStream()) {
                var miniatura = miniaturas.generar(entrada);
                if (miniatura.isPresent()) {
                    claveMiniatura = UUID.randomUUID();
                    guardarEnStorage(CARPETA_MINIATURAS + claveMiniatura, new ByteArrayInputStream(miniatura.get()));
                }
            }
        }

        var documento = new Documento(NombresFichero.sinExtension(nombre), nombre, tipo, fichero.tamano(),
                claveFichero, claveMiniatura, subidoPor);
        documento.actualizar(documento.getNombreVisible(), datos.descripcion(), datos.personaId(),
                datos.seccionIds());
        return documentos.save(documento);
    }

    /** Guarda el fichero y, si luego la transacción se deshace, lo borra para no dejar huérfanos en disco. */
    private void guardarEnStorage(String clave, InputStream contenido) {
        storage.guardar(clave, contenido);
        alTerminarTransaccion(false, List.of(clave));
    }

    /**
     * Borra ficheros del disco al terminar la transacción: tras confirmarla ({@code siConfirma = true},
     * para borrados) o tras deshacerla ({@code false}, para limpiar subidas fallidas).
     */
    private void alTerminarTransaccion(boolean siConfirma, List<String> claves) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                var borrar = siConfirma ? estado == STATUS_COMMITTED : estado == STATUS_ROLLED_BACK;
                if (borrar) {
                    claves.forEach(clave -> {
                        try {
                            storage.borrar(clave);
                        } catch (RuntimeException e) {
                            log.warn("No se pudo borrar el fichero {}: {}", clave, e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void validarSeccionesYPersona(Collection<Long> seccionIds, Long personaId) {
        if (!seccionService.existenTodas(seccionIds)) {
            throw new ReglaNegocioException("secciones.elegidaNoExiste");
        }
        if (personaId != null && !personaService.existe(personaId)) {
            throw new ReglaNegocioException("personas.elegidaNoExiste");
        }
    }

    private Documento buscarEntidad(Long id) {
        return documentos.findById(id).orElseThrow(() -> new NoEncontradoException("documentos.noExiste", id));
    }

    public long tamanoMaximoBytes() {
        return propiedades.tamanoMaximo().toBytes();
    }

    /** Tamaño máximo por fichero, para mostrarlo en pantalla ("20 MB"). */
    public String tamanoMaximoLegible() {
        var bytes = propiedades.tamanoMaximo().toBytes();
        return bytes >= 1024 * 1024 ? bytes / (1024 * 1024) + " MB" : bytes / 1024 + " KB";
    }

    /** Para que un "%" o "_" escrito por el usuario se busque literalmente y no como comodín. */
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private DocumentoDto aDto(Documento documento) {
        return aDtos(List.of(documento)).getFirst();
    }

    /** Convierte a DTOs buscando personas, secciones y usuarios de una vez (no una consulta por fila). */
    private List<DocumentoDto> aDtos(List<Documento> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        var personas = personaService.buscarPorIds(
                lista.stream().map(Documento::getPersonaId).filter(Objects::nonNull).distinct().toList());
        var secciones = seccionService.buscarPorIds(
                lista.stream().flatMap(d -> d.getSeccionIds().stream()).distinct().toList());
        var usuarios = usuarioService.usernamesPorIds(
                lista.stream().map(Documento::getSubidoPorId).distinct().toList());
        return lista.stream().map(d -> new DocumentoDto(
                d.getId(),
                d.getNombreVisible(),
                d.getNombreOriginal(),
                d.getTipoMime(),
                d.getTamanoBytes(),
                d.getDescripcion(),
                d.getPersonaId() == null ? null : personas.get(d.getPersonaId()),
                d.getSeccionIds().stream()
                        .map(secciones::get)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(SeccionDto::nombre, String.CASE_INSENSITIVE_ORDER))
                        .toList(),
                usuarios.getOrDefault(d.getSubidoPorId(), ""),
                d.getSubidoEn(),
                d.getClaveMiniatura() != null))
                .toList();
    }

    /** Tipo de fichero fuera de la lista blanca. */
    private static final class TipoNoPermitido extends ErrorAplicacion {
        TipoNoPermitido(String tipo) {
            super("documentos.tipo", tipo);
        }
    }
}
