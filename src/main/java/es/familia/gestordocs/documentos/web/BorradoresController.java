package es.familia.gestordocs.documentos.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import es.familia.gestordocs.compartido.NoEncontradoException;
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.documentos.*;
import es.familia.gestordocs.escaneo.EscaneoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/documentos/escaneos")
class BorradoresController {
    private final BorradorEscaneoService borradores;
    private final DocumentoService documentos;

    BorradoresController(BorradorEscaneoService borradores, DocumentoService documentos) {
        this.borradores = borradores; this.documentos = documentos;
    }

    @PostMapping
    ResponseEntity<?> crear(@RequestParam("file") MultipartFile file) {
        return respuesta(borradores.crear(new FicheroSubido(file.getOriginalFilename(), file.getSize(), file)));
    }

    @PostMapping("/{id}/filtro")
    ResponseEntity<?> filtrar(@PathVariable UUID id, @RequestParam String variante) {
        return respuesta(borradores.filtrar(id, variante));
    }

    @GetMapping("/{id}/imagen")
    ResponseEntity<byte[]> imagen(@PathVariable UUID id, @RequestParam String variante) {
        var imagen = borradores.imagen(id, variante);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(imagen.tipo()))
                .contentLength(imagen.contenido().length).body(imagen.contenido());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<?> descartar(@PathVariable UUID id) {
        borradores.descartar(id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping(value = "/guardar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> guardar(@Valid @RequestPart("datos") GuardadoBorradores solicitud,
                             @RequestParam(name = "ficheros", required = false) List<MultipartFile> ficheros) {
        var fotos = solicitud.fotos() == null ? List.<SeleccionBorrador>of() : solicitud.fotos();
        if (fotos.stream().anyMatch(f -> f == null || f.id() == null)) {
            throw new ReglaNegocioException("La selección de fotos pendientes no es válida.");
        }
        var recibidos = (ficheros == null ? List.<MultipartFile>of() : ficheros).stream()
                .map(f -> new FicheroSubido(f.getOriginalFilename(), f.getSize(), f)).toList();
        var datos = new SubidaForm(solicitud.seccionIds(), solicitud.personaId(), solicitud.descripcion());
        return respuesta(documentos.subir(datos, recibidos, fotos));
    }

    record GuardadoBorradores(List<Long> seccionIds, Long personaId,
                              @Size(max = 2000) String descripcion,
                              @Size(max = 20) List<SeleccionBorrador> fotos) { }

    @ExceptionHandler({ReglaNegocioException.class, EscaneoException.class})
    ResponseEntity<?> error(RuntimeException e) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(NoEncontradoException.class)
    ResponseEntity<?> ausente(NoEncontradoException e) {
        return ResponseEntity.status(404).cacheControl(CacheControl.noStore()).body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<?> validacion(org.springframework.web.bind.MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("mensaje", "Revisa los datos: la descripción admite hasta 2000 caracteres y el lote hasta 20 fotos."));
    }

    private static ResponseEntity<?> respuesta(Object contenido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contenido);
    }
}
