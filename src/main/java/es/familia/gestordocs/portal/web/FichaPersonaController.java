package es.familia.gestordocs.portal.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import es.familia.gestordocs.documentos.DocumentoDto;
import es.familia.gestordocs.documentos.DocumentoService;
import es.familia.gestordocs.personas.PersonaService;

/** Ficha de una persona: todos sus documentos, agrupados por sección. */
@Controller
class FichaPersonaController {

    static final String SIN_CLASIFICAR = "Sin clasificar";

    private final PersonaService personaService;
    private final DocumentoService documentoService;

    FichaPersonaController(PersonaService personaService, DocumentoService documentoService) {
        this.personaService = personaService;
        this.documentoService = documentoService;
    }

    @GetMapping("/personas/{id}")
    String ficha(@PathVariable Long id, Model model) {
        var documentos = documentoService.porPersona(id);
        model.addAttribute("persona", personaService.obtener(id));
        model.addAttribute("total", documentos.size());
        model.addAttribute("grupos", agruparPorSeccion(documentos));
        return "persona";
    }

    /**
     * Nombre de sección → documentos, por orden alfabético y con "Sin clasificar" al final.
     * Un documento que está en dos secciones aparece en los dos grupos.
     */
    static Map<String, List<DocumentoDto>> agruparPorSeccion(List<DocumentoDto> documentos) {
        var porSeccion = new TreeMap<String, List<DocumentoDto>>(String.CASE_INSENSITIVE_ORDER);
        var sinSeccion = new ArrayList<DocumentoDto>();
        for (var documento : documentos) {
            if (documento.secciones().isEmpty()) {
                sinSeccion.add(documento);
            }
            documento.secciones().forEach(s -> porSeccion.computeIfAbsent(s.nombre(), n -> new ArrayList<>()).add(documento));
        }
        var grupos = new LinkedHashMap<String, List<DocumentoDto>>(porSeccion);
        if (!sinSeccion.isEmpty()) {
            grupos.put(SIN_CLASIFICAR, sinSeccion);
        }
        return grupos;
    }
}
