package es.familia.gestordocs.personas.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import es.familia.gestordocs.personas.PersonaForm;
import es.familia.gestordocs.personas.PersonaService;
import jakarta.validation.Valid;

/** Alta y edición de personas. El borrado (que avisa de los documentos afectados) está en el módulo portal. */
@Controller
@RequestMapping("/admin/personas")
class AdminPersonasController {

    private static final String FORMULARIO = "admin/personas/formulario";

    private final PersonaService personaService;

    AdminPersonasController(PersonaService personaService) {
        this.personaService = personaService;
    }

    @GetMapping
    String lista(Model model) {
        model.addAttribute("personas", personaService.listar());
        return "admin/personas/lista";
    }

    @GetMapping("/nueva")
    String nueva(Model model) {
        model.addAttribute("form", PersonaForm.vacio());
        return FORMULARIO;
    }

    @PostMapping("/nueva")
    String crear(@Valid @ModelAttribute("form") PersonaForm form, BindingResult errores,
            RedirectAttributes redirect) {
        if (errores.hasErrors()) {
            return FORMULARIO;
        }
        var persona = personaService.crear(form);
        redirect.addFlashAttribute("mensaje", persona.nombreCompleto() + " añadida.");
        return "redirect:/admin/personas";
    }

    @GetMapping("/{id}/editar")
    String editar(@PathVariable Long id, Model model) {
        model.addAttribute("form", PersonaForm.desde(personaService.obtener(id)));
        model.addAttribute("id", id);
        return FORMULARIO;
    }

    @PostMapping("/{id}/editar")
    String actualizar(@PathVariable Long id, @Valid @ModelAttribute("form") PersonaForm form,
            BindingResult errores, Model model, RedirectAttributes redirect) {
        if (errores.hasErrors()) {
            model.addAttribute("id", id);
            return FORMULARIO;
        }
        var persona = personaService.actualizar(id, form);
        redirect.addFlashAttribute("mensaje", persona.nombreCompleto() + " guardada.");
        return "redirect:/admin/personas";
    }
}
