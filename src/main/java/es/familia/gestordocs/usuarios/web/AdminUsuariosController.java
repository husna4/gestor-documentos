package es.familia.gestordocs.usuarios.web;

import java.util.Objects;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.usuarios.EdicionUsuarioForm;
import es.familia.gestordocs.usuarios.NuevoUsuarioForm;
import es.familia.gestordocs.usuarios.Rol;
import es.familia.gestordocs.usuarios.UsuarioDto;
import es.familia.gestordocs.usuarios.UsuarioService;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/usuarios")
class AdminUsuariosController {

    private static final String NUEVO = "admin/usuarios/nuevo";
    private static final String EDITAR = "admin/usuarios/editar";

    private final UsuarioService usuarioService;
    private final PersonaService personaService;

    AdminUsuariosController(UsuarioService usuarioService, PersonaService personaService) {
        this.usuarioService = usuarioService;
        this.personaService = personaService;
    }

    @GetMapping
    String lista(Model model) {
        var usuarios = usuarioService.listar();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("personas", personaService.buscarPorIds(
                usuarios.stream().map(UsuarioDto::personaId).filter(Objects::nonNull).toList()));
        model.addAttribute("idActual", usuarioService.idUsuarioActual().orElse(null));
        return "admin/usuarios/lista";
    }

    @GetMapping("/nuevo")
    String nuevo(Model model) {
        model.addAttribute("form", NuevoUsuarioForm.vacio());
        return prepararFormulario(model, NUEVO);
    }

    @PostMapping("/nuevo")
    String crear(@Valid @ModelAttribute("form") NuevoUsuarioForm form, BindingResult errores, Model model,
            RedirectAttributes redirect) {
        if (errores.hasErrors()) {
            return prepararFormulario(model, NUEVO);
        }
        try {
            var usuario = usuarioService.crear(form);
            redirect.addFlashAttribute("mensaje", "Usuario «" + usuario.username()
                    + "» creado. Tendrá que cambiar la contraseña la primera vez que entre.");
            return "redirect:/admin/usuarios";
        } catch (ReglaNegocioException e) {
            errores.reject("regla", e.getMessage());
            return prepararFormulario(model, NUEVO);
        }
    }

    @GetMapping("/{id}/editar")
    String editar(@PathVariable Long id, Model model) {
        var usuario = usuarioService.obtener(id);
        model.addAttribute("form", EdicionUsuarioForm.desde(usuario));
        model.addAttribute("usuario", usuario);
        return prepararFormulario(model, EDITAR);
    }

    @PostMapping("/{id}/editar")
    String actualizar(@PathVariable Long id, @Valid @ModelAttribute("form") EdicionUsuarioForm form,
            BindingResult errores, Model model, RedirectAttributes redirect) {
        model.addAttribute("usuario", usuarioService.obtener(id));
        if (errores.hasErrors()) {
            return prepararFormulario(model, EDITAR);
        }
        try {
            var usuario = usuarioService.actualizar(id, form);
            redirect.addFlashAttribute("mensaje", "Usuario «" + usuario.username() + "» guardado.");
            return "redirect:/admin/usuarios";
        } catch (ReglaNegocioException e) {
            errores.reject("regla", e.getMessage());
            return prepararFormulario(model, EDITAR);
        }
    }

    @PostMapping("/{id}/password")
    String resetearPassword(@PathVariable Long id, @RequestParam String temporal, RedirectAttributes redirect) {
        try {
            usuarioService.resetearPassword(id, temporal);
            redirect.addFlashAttribute("mensaje",
                    "Contraseña temporal puesta. El usuario tendrá que cambiarla al entrar.");
        } catch (ReglaNegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios/" + id + "/editar";
    }

    @PostMapping("/{id}/borrar")
    String borrar(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            usuarioService.eliminar(id);
            redirect.addFlashAttribute("mensaje", "Usuario borrado.");
        } catch (ReglaNegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    private String prepararFormulario(Model model, String vista) {
        model.addAttribute("roles", Rol.values());
        model.addAttribute("personas", personaService.listar());
        return vista;
    }
}
