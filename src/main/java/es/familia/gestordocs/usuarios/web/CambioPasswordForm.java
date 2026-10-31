package es.familia.gestordocs.usuarios.web;

import es.familia.gestordocs.usuarios.UsuarioService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos del formulario de cambio de contraseña. */
record CambioPasswordForm(
        @NotBlank(message = "Escribe tu contraseña actual") String actual,
        @NotBlank(message = "Escribe la nueva contraseña")
        @Size(min = UsuarioService.LONGITUD_MINIMA_PASSWORD, max = 100,
                message = "Debe tener al menos {min} caracteres") String nueva,
        @NotBlank(message = "Repite la nueva contraseña") String repetida) {

    static CambioPasswordForm vacio() {
        return new CambioPasswordForm("", "", "");
    }
}
