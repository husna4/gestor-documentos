package es.familia.gestordocs.portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import es.familia.gestordocs.documentos.DocumentoService;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.secciones.SeccionService;

/** Inicio: accesos rápidos a secciones y personas, y los últimos documentos subidos. */
@Controller
class InicioController {

    static final int DOCUMENTOS_RECIENTES = 12;

    private final SeccionService seccionService;
    private final PersonaService personaService;
    private final DocumentoService documentoService;

    InicioController(SeccionService seccionService, PersonaService personaService,
            DocumentoService documentoService) {
        this.seccionService = seccionService;
        this.personaService = personaService;
        this.documentoService = documentoService;
    }

    @GetMapping("/")
    String inicio(Model model) {
        model.addAttribute("secciones", seccionService.listar());
        model.addAttribute("documentosPorSeccion", documentoService.contarPorCadaSeccion());
        model.addAttribute("sinClasificar", documentoService.contarSinClasificar());
        model.addAttribute("personas", personaService.listar());
        model.addAttribute("recientes", documentoService.recientes(DOCUMENTOS_RECIENTES));
        return "inicio";
    }
}
