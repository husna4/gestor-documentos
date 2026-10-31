package es.familia.gestordocs.usuarios.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.usuarios.UsuarioService;
import jakarta.validation.Valid;

/** Cuenta del usuario con sesión iniciada: de momento, solo cambiar la contraseña. */
@Controller
@RequestMapping("/cuenta/password")
class CuentaController {

    private static final String VISTA = "cuenta/password";

    private final UsuarioService usuarioService;

    CuentaController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    String formulario(Model model) {
        model.addAttribute("form", CambioPasswordForm.vacio());
        return prepararVista(model);
    }

    @PostMapping
    String cambiar(@Valid @ModelAttribute("form") CambioPasswordForm form, BindingResult errores,
            Model model, RedirectAttributes redirect) {
        if (!errores.hasFieldErrors("repetida") && !form.repetida().equals(form.nueva())) {
            errores.rejectValue("repetida", "no-coincide", "Las contraseñas no coinciden");
        }
        if (errores.hasErrors()) {
            return prepararVista(model);
        }
        try {
            usuarioService.cambiarPasswordPropia(form.actual(), form.nueva());
        } catch (ReglaNegocioException e) {
            errores.reject("regla", e.getMessage());
            return prepararVista(model);
        }
        redirect.addFlashAttribute("mensaje", "Contraseña cambiada correctamente.");
        return "redirect:/";
    }

    private String prepararVista(Model model) {
        model.addAttribute("obligatorio", usuarioService.usuarioActual().debeCambiarPassword());
        return VISTA;
    }
}
