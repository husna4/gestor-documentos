package es.familia.gestordocs.usuarios.web;

import es.familia.gestordocs.usuarios.UsuarioService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos del formulario de cambio de contraseña. */
record CambioPasswordForm(
        @NotBlank(message = "{validacion.actual}") String actual,
        @NotBlank(message = "{validacion.nueva}")
        @Size(min = UsuarioService.LONGITUD_MINIMA_PASSWORD, max = 100,
                message = "{validacion.passwordLongitud}") String nueva,
        @NotBlank(message = "{validacion.repetida}") String repetida) {

    static CambioPasswordForm vacio() {
        return new CambioPasswordForm("", "", "");
    }
}
