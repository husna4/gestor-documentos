package es.familia.gestordocs.secciones.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.secciones.SeccionForm;
import es.familia.gestordocs.secciones.SeccionService;
import jakarta.validation.Valid;

/** Alta y edición de secciones. El borrado (que avisa de los documentos afectados) está en el módulo portal. */
@Controller
@RequestMapping("/admin/secciones")
class AdminSeccionesController {

    private static final String FORMULARIO = "admin/secciones/formulario";

    private final SeccionService seccionService;

    AdminSeccionesController(SeccionService seccionService) {
        this.seccionService = seccionService;
    }

    @GetMapping
    String lista(Model model) {
        model.addAttribute("secciones", seccionService.listar());
        return "admin/secciones/lista";
    }

    @GetMapping("/nueva")
    String nueva(Model model) {
        model.addAttribute("form", SeccionForm.vacio());
        return FORMULARIO;
    }

    @PostMapping("/nueva")
    String crear(@Valid @ModelAttribute("form") SeccionForm form, BindingResult errores,
            RedirectAttributes redirect) {
        if (errores.hasErrors()) {
            return FORMULARIO;
        }
        try {
            var seccion = seccionService.crear(form);
            redirect.addFlashAttribute("mensaje", "Sección «" + seccion.nombre() + "» creada.");
            return "redirect:/admin/secciones";
        } catch (ReglaNegocioException e) {
            errores.rejectValue("nombre", "repetido", e.getMessage());
            return FORMULARIO;
        }
    }

    @GetMapping("/{id}/editar")
    String editar(@PathVariable Long id, Model model) {
        model.addAttribute("form", SeccionForm.desde(seccionService.obtener(id)));
        model.addAttribute("id", id);
        return FORMULARIO;
    }

    @PostMapping("/{id}/editar")
    String actualizar(@PathVariable Long id, @Valid @ModelAttribute("form") SeccionForm form,
            BindingResult errores, Model model, RedirectAttributes redirect) {
        model.addAttribute("id", id);
        if (errores.hasErrors()) {
            return FORMULARIO;
        }
        try {
            var seccion = seccionService.actualizar(id, form);
            redirect.addFlashAttribute("mensaje", "Sección «" + seccion.nombre() + "» guardada.");
            return "redirect:/admin/secciones";
        } catch (ReglaNegocioException e) {
            errores.rejectValue("nombre", "repetido", e.getMessage());
            return FORMULARIO;
        }
    }
}
