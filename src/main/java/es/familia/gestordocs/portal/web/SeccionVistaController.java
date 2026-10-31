package es.familia.gestordocs.portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import es.familia.gestordocs.documentos.DocumentoService;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionService;

@Controller
class SeccionVistaController {

    private final SeccionService seccionService;
    private final PersonaService personaService;
    private final DocumentoService documentoService;

    SeccionVistaController(SeccionService seccionService, PersonaService personaService,
            DocumentoService documentoService) {
        this.seccionService = seccionService;
        this.personaService = personaService;
        this.documentoService = documentoService;
    }

    /**
     * Documentos de una sección, con filtro opcional por persona. Al cambiar el filtro, htmx pide
     * solo la lista (cabecera HX-Request) y la sustituye sin recargar la página.
     */
    @GetMapping("/secciones/{id}")
    String seccion(@PathVariable Long id, @RequestParam(required = false) Long persona,
            @RequestHeader(name = "HX-Request", required = false) String htmx, Model model) {
        model.addAttribute("seccion", seccionService.obtener(id));
        model.addAttribute("personaFiltro", persona);
        model.addAttribute("documentos", documentoService.porSeccion(id, persona));
        if (htmx != null) {
            return "seccion :: listado";
        }
        model.addAttribute("personas", personaService.listar());
        return "seccion";
    }

    @GetMapping("/sin-clasificar")
    String sinClasificar(Model model) {
        model.addAttribute("documentos", documentoService.sinClasificar());
        return "sin-clasificar";
    }
}
