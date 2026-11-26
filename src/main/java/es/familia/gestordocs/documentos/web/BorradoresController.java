package es.familia.gestordocs.documentos.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import es.familia.gestordocs.almacenamiento.AlmacenamientoException;
import es.familia.gestordocs.compartido.ErrorAplicacion;
import es.familia.gestordocs.compartido.Mensaje;
import es.familia.gestordocs.compartido.NoEncontradoException;
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.compartido.web.Mensajes;
import es.familia.gestordocs.documentos.*;
import es.familia.gestordocs.escaneo.EscaneoException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/documentos/escaneos")
class BorradoresController {
    private final Mensajes mensajes;
    private final BorradorEscaneoService borradores;
    private final DocumentoService documentos;

    BorradoresController(BorradorEscaneoService borradores, DocumentoService documentos,
            Mensajes mensajes) {
        this.mensajes = mensajes; this.borradores = borradores; this.documentos = documentos;
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
            throw new ReglaNegocioException("escaneo.seleccionInvalida");
        }
        var recibidos = (ficheros == null ? List.<MultipartFile>of() : ficheros).stream()
                .map(f -> new FicheroSubido(f.getOriginalFilename(), f.getSize(), f)).toList();
        var datos = new SubidaForm(solicitud.seccionIds(), solicitud.personaId(), solicitud.descripcion());
        var resultado = documentos.subir(datos, recibidos, fotos);
        var caducado = resultado.errores().stream().anyMatch(e -> e.argumentos().stream()
                .anyMatch(a -> a instanceof Mensaje m
                        && "escaneo.caducado".equals(m.codigo())));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Borrador-Caducado", Boolean.toString(caducado))
                .body(Map.of("subidos", resultado.subidos(),
                "errores", resultado.errores().stream().map(mensajes::texto).toList(),
                "avisos", resultado.avisos().stream().map(mensajes::texto).toList()));
    }

    record GuardadoBorradores(List<Long> seccionIds, Long personaId,
                              @Size(max = 2000) String descripcion,
                              @Size(max = 20) List<SeleccionBorrador> fotos) { }

    @ExceptionHandler({ReglaNegocioException.class, EscaneoException.class})
    ResponseEntity<?> error(ErrorAplicacion e) {
        if (e instanceof EscaneoException) {
            org.slf4j.LoggerFactory.getLogger(BorradoresController.class).warn("Fallo recuperable de escaneo", e);
        }
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("mensaje", mensajes.texto(e)));
    }

    @ExceptionHandler(NoEncontradoException.class)
    ResponseEntity<?> ausente(NoEncontradoException e) {
        return ResponseEntity.status(404).cacheControl(CacheControl.noStore()).body(Map.of("mensaje", mensajes.texto(e)));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<?> validacion(org.springframework.web.bind.MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("mensaje", mensajes.texto(Mensaje.de("escaneo.validacion"))));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<?> datosInvalidos(Exception error) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("mensaje", mensajes.texto(Mensaje.de("error.datosInvalidos"))));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> demasiadoGrande(Exception error) {
        return ResponseEntity.status(413).cacheControl(CacheControl.noStore())
                .body(Map.of("mensaje", mensajes.texto(Mensaje.de("error.grandeAyuda"))));
    }

    @ExceptionHandler(AlmacenamientoException.class)
    ResponseEntity<?> almacenamiento(AlmacenamientoException error) {
        org.slf4j.LoggerFactory.getLogger(BorradoresController.class).error("Fallo de almacenamiento", error);
        return ResponseEntity.internalServerError().cacheControl(CacheControl.noStore())
                .body(Map.of("mensaje", mensajes.texto(Mensaje.de("error.reintentar"))));
    }

    private static ResponseEntity<?> respuesta(Object contenido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contenido);
    }
}
