package es.familia.gestordocs.documentos;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionService;

/**
 * Genera ZIPs con los documentos de una persona o de una sección. Se escriben directamente
 * en la respuesta HTTP según se leen los ficheros, sin cargar el ZIP entero en memoria.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class ZipService {

    static final String CARPETA_SIN_CLASIFICAR = "Sin clasificar";

    private final DocumentoService documentoService;
    private final PersonaService personaService;
    private final SeccionService seccionService;

    ZipService(DocumentoService documentoService, PersonaService personaService, SeccionService seccionService) {
        this.documentoService = documentoService;
        this.personaService = personaService;
        this.seccionService = seccionService;
    }

    public String nombreZipPersona(Long personaId) {
        return NombresFichero.seguroParaDisco(personaService.obtener(personaId).nombreCompleto()) + ".zip";
    }

    public String nombreZipSeccion(Long seccionId, Long personaId) {
        var nombre = seccionService.obtener(seccionId).nombre();
        if (personaId != null) {
            nombre += " - " + personaService.obtener(personaId).nombreCompleto();
        }
        return NombresFichero.seguroParaDisco(nombre) + ".zip";
    }

    /** Todos los documentos de la persona, en una carpeta por sección (un documento en dos secciones va en ambas). */
    public void escribirZipPersona(Long personaId, OutputStream salida) throws IOException {
        try (var zip = new ZipOutputStream(salida)) {
            var nombres = new NombresUnicos();
            for (var documento : documentoService.porPersona(personaId)) {
                var carpetas = documento.secciones().isEmpty()
                        ? List.of(CARPETA_SIN_CLASIFICAR)
                        : documento.secciones().stream().map(s -> s.nombre()).toList();
                for (var carpeta : carpetas) {
                    anadir(zip, NombresFichero.seguroParaDisco(carpeta), documento, nombres);
                }
            }
        }
    }

    /** Los documentos de la sección (opcionalmente solo los de una persona), sin carpetas. */
    public void escribirZipSeccion(Long seccionId, Long personaId, OutputStream salida) throws IOException {
        try (var zip = new ZipOutputStream(salida)) {
            var nombres = new NombresUnicos();
            for (var documento : documentoService.porSeccion(seccionId, personaId)) {
                anadir(zip, null, documento, nombres);
            }
        }
    }

    private void anadir(ZipOutputStream zip, String carpeta, DocumentoDto documento, NombresUnicos nombres)
            throws IOException {
        var contenido = documentoService.contenido(documento.id());
        var ruta = nombres.reservar(carpeta, contenido.nombreDescarga());
        zip.putNextEntry(new ZipEntry(ruta));
        try (var entrada = contenido.recurso().getInputStream()) {
            entrada.transferTo(zip);
        }
        zip.closeEntry();
    }

    /** Dentro de una carpeta del ZIP no puede haber dos ficheros iguales: el segundo pasa a "factura (2).pdf". */
    static final class NombresUnicos {

        private final Map<String, Set<String>> usadosPorCarpeta = new HashMap<>();

        String reservar(String carpeta, String nombre) {
            var usados = usadosPorCarpeta.computeIfAbsent(carpeta == null ? "" : carpeta, c -> new HashSet<>());
            var candidato = nombre;
            var base = NombresFichero.sinExtension(nombre);
            var extension = NombresFichero.extension(nombre);
            for (int n = 2; !usados.add(candidato.toLowerCase(Locale.ROOT)); n++) {
                candidato = base + " (" + n + ")" + (extension.isEmpty() ? "" : "." + extension);
            }
            return carpeta == null ? candidato : carpeta + "/" + candidato;
        }
    }
}
