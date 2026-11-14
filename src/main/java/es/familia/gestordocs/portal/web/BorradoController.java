package es.familia.gestordocs.portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import es.familia.gestordocs.compartido.Mensaje;
import es.familia.gestordocs.documentos.DocumentoService;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionService;

/**
 * Borrado de personas y secciones. Vive en el portal porque, antes de borrar, cuenta cuántos
 * documentos se verán afectados (dato del módulo documentos) y lo explica en la confirmación.
 */
@Controller
class BorradoController {

    private static final String CONFIRMAR = "admin/confirmar-borrado";

    private final PersonaService personaService;
    private final SeccionService seccionService;
    private final DocumentoService documentoService;

    BorradoController(PersonaService personaService, SeccionService seccionService,
            DocumentoService documentoService) {
        this.personaService = personaService;
        this.seccionService = seccionService;
        this.documentoService = documentoService;
    }

    @GetMapping("/admin/personas/{id}/borrar")
    String confirmarPersona(@PathVariable Long id, Model model) {
        var persona = personaService.obtener(id);
        var documentos = documentoService.contarPorPersona(id);
        model.addAttribute("titulo", Mensaje.de("personas.confirmarBorrado", persona.nombreCompleto()));
        model.addAttribute("documentos", documentos);
        model.addAttribute("consecuencia", Mensaje.de(documentos == 0 ? "borrado.sinDocumentos" : "borrado.personaConsecuencia"));
        model.addAttribute("accion", "/admin/personas/" + id + "/borrar");
        model.addAttribute("volver", "/admin/personas");
        return CONFIRMAR;
    }

    @PostMapping("/admin/personas/{id}/borrar")
    String borrarPersona(@PathVariable Long id, RedirectAttributes redirect) {
        var persona = personaService.obtener(id);
        personaService.eliminar(id);
        redirect.addFlashAttribute("mensaje", Mensaje.de("personas.borrada", persona.nombreCompleto()));
        return "redirect:/admin/personas";
    }

    @GetMapping("/admin/secciones/{id}/borrar")
    String confirmarSeccion(@PathVariable Long id, Model model) {
        var seccion = seccionService.obtener(id);
        var documentos = documentoService.contarPorSeccion(id);
        model.addAttribute("titulo", Mensaje.de("secciones.confirmarBorrado", seccion.nombre()));
        model.addAttribute("documentos", documentos);
        model.addAttribute("consecuencia", Mensaje.de(documentos == 0 ? "borrado.sinDocumentos" : "borrado.seccionConsecuencia"));
        model.addAttribute("accion", "/admin/secciones/" + id + "/borrar");
        model.addAttribute("volver", "/admin/secciones");
        return CONFIRMAR;
    }

    @PostMapping("/admin/secciones/{id}/borrar")
    String borrarSeccion(@PathVariable Long id, RedirectAttributes redirect) {
        var seccion = seccionService.obtener(id);
        seccionService.eliminar(id);
        redirect.addFlashAttribute("mensaje", Mensaje.de("secciones.borrada", seccion.nombre()));
        return "redirect:/admin/secciones";
    }
}
