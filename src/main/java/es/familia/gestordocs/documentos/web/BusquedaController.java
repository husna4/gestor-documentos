package es.familia.gestordocs.documentos.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import es.familia.gestordocs.documentos.DocumentoService;

/**
 * Buscador. Con htmx, mientras se escribe se piden solo los resultados (cabecera HX-Request)
 * y se sustituyen en la página sin recargarla. Sin JavaScript, funciona como un formulario normal.
 */
@Controller
class BusquedaController {

    private final DocumentoService documentoService;

    BusquedaController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @GetMapping("/buscar")
    String buscar(@RequestParam(name = "q", defaultValue = "") String texto,
            @RequestHeader(name = "HX-Request", required = false) String htmx, Model model) {
        model.addAttribute("q", texto);
        model.addAttribute("documentos", documentoService.buscar(texto));
        return htmx != null ? "busqueda :: resultados" : "busqueda";
    }
}
