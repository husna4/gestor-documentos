package es.familia.gestordocs.documentos.web;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.documentos.ContenidoDocumento;
import es.familia.gestordocs.documentos.DocumentoDto;
import es.familia.gestordocs.documentos.DocumentoService;
import es.familia.gestordocs.documentos.EdicionDocumentoForm;
import es.familia.gestordocs.documentos.FicheroSubido;
import es.familia.gestordocs.documentos.SubidaForm;
import es.familia.gestordocs.documentos.TiposFichero;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionService;
import jakarta.validation.Valid;

/**
 * Pantallas de un documento. Los ficheros nunca se sirven como estáticos: siempre pasan por aquí,
 * y Spring Security ya ha comprobado que hay sesión iniciada.
 */
@Controller
@RequestMapping("/documentos")
class DocumentosController {

    private static final String SUBIR = "documentos/subir";
    private static final String EDITAR = "documentos/editar";

    private final DocumentoService documentoService;
    private final SeccionService seccionService;
    private final PersonaService personaService;

    DocumentosController(DocumentoService documentoService, SeccionService seccionService,
            PersonaService personaService) {
        this.documentoService = documentoService;
        this.seccionService = seccionService;
        this.personaService = personaService;
    }

    @GetMapping("/{id}")
    String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("documento", documentoService.obtener(id));
        return "documentos/detalle";
    }

    /** Para el visor: imágenes y PDF se muestran en el navegador; el resto se descarga. */
    @GetMapping("/{id}/ver")
    ResponseEntity<Resource> ver(@PathVariable Long id) {
        var contenido = documentoService.contenido(id);
        return enviar(contenido, contenido.previsualizable());
    }

    @GetMapping("/{id}/descargar")
    ResponseEntity<Resource> descargar(@PathVariable Long id) {
        return enviar(documentoService.contenido(id), false);
    }

    @GetMapping("/{id}/miniatura")
    ResponseEntity<Resource> miniatura(@PathVariable Long id) {
        return documentoService.miniatura(id)
                .map(imagen -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        // "private": el navegador puede guardarla, pero ningún proxy intermedio.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                        .body(imagen))
                .orElse(ResponseEntity.notFound().build());
    }

    // ---- Subida (solo admin: lo comprueban SecurityConfig y el @PreAuthorize del service) ----

    @GetMapping("/subir")
    String formularioSubida(@RequestParam(required = false) Long seccion,
            @RequestParam(required = false) Long persona, Model model) {
        model.addAttribute("form", new SubidaForm(seccion == null ? List.of() : List.of(seccion), persona, ""));
        return prepararFormulario(model, SUBIR);
    }

    @PostMapping("/subir")
    String subir(@Valid @ModelAttribute("form") SubidaForm form, BindingResult errores,
            @RequestParam(name = "ficheros", required = false) List<MultipartFile> ficheros,
            Model model, RedirectAttributes redirect) {
        if (errores.hasErrors()) {
            return prepararFormulario(model, SUBIR);
        }
        // El botón de cámara y el selector de ficheros envían cada uno su campo: ignoramos los vacíos.
        var recibidos = (ficheros == null ? List.<MultipartFile>of() : ficheros).stream()
                .filter(f -> !f.isEmpty() || StringUtils.hasText(f.getOriginalFilename()))
                .map(f -> new FicheroSubido(f.getOriginalFilename(), f.getSize(), f))
                .toList();
        try {
            var resultado = documentoService.subir(form, recibidos);
            redirect.addFlashAttribute("subidos", resultado.subidos().stream().map(DocumentoDto::nombreVisible).toList());
            redirect.addFlashAttribute("rechazados", resultado.errores());
            return "redirect:/documentos/subir";
        } catch (ReglaNegocioException e) {
            errores.reject("regla", e.getMessage());
            return prepararFormulario(model, SUBIR);
        }
    }

    // ---- Edición y borrado (solo admin) ----

    @GetMapping("/{id}/editar")
    String editar(@PathVariable Long id, Model model) {
        var documento = documentoService.obtener(id);
        model.addAttribute("documento", documento);
        model.addAttribute("form", EdicionDocumentoForm.desde(documento));
        return prepararFormulario(model, EDITAR);
    }

    @PostMapping("/{id}/editar")
    String actualizar(@PathVariable Long id, @Valid @ModelAttribute("form") EdicionDocumentoForm form,
            BindingResult errores, Model model, RedirectAttributes redirect) {
        if (!errores.hasErrors()) {
            try {
                documentoService.editar(id, form);
                redirect.addFlashAttribute("mensaje", "Documento guardado.");
                return "redirect:/documentos/" + id;
            } catch (ReglaNegocioException e) {
                errores.reject("regla", e.getMessage());
            }
        }
        model.addAttribute("documento", documentoService.obtener(id));
        return prepararFormulario(model, EDITAR);
    }

    @PostMapping("/{id}/borrar")
    String borrar(@PathVariable Long id, RedirectAttributes redirect) {
        var documento = documentoService.obtener(id);
        documentoService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "Documento «" + documento.nombreVisible() + "» borrado.");
        return "redirect:/";
    }

    // ---- Auxiliares ----

    private String prepararFormulario(Model model, String vista) {
        model.addAttribute("secciones", seccionService.listar());
        model.addAttribute("personas", personaService.listar());
        model.addAttribute("tiposAceptados", TiposFichero.ACCEPT_HTML);
        model.addAttribute("tamanoMaximo", documentoService.tamanoMaximoLegible());
        model.addAttribute("tamanoMaximoBytes", documentoService.tamanoMaximoBytes());
        return vista;
    }

    private static ResponseEntity<Resource> enviar(ContenidoDocumento contenido, boolean enNavegador) {
        var disposicion = (enNavegador ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(contenido.nombreDescarga(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(enNavegador ? MediaType.parseMediaType(contenido.tipoMime())
                        : MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(contenido.tamano())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                // El navegador no debe "adivinar" otro tipo (p. ej. tratar un fichero como HTML ejecutable).
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(contenido.recurso());
    }
}
