package es.familia.gestordocs.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un usuario. La contraseña es temporal: el usuario tendrá que cambiarla al entrar.
 *
 * @param personaId persona de la familia vinculada (opcional)
 */
public record NuevoUsuarioForm(
        @NotBlank(message = "{validacion.usuario}")
        @Pattern(regexp = "[A-Za-z0-9._-]{3,50}",
                message = "{validacion.username}")
        String username,
        @NotBlank(message = "{validacion.password}")
        @Size(min = UsuarioService.LONGITUD_MINIMA_PASSWORD, max = 100, message = "{validacion.passwordLongitud}")
        String password,
        @NotNull(message = "{validacion.rol}") Rol rol,
        Long personaId,
        Idioma idioma) {

    public NuevoUsuarioForm {
        if (idioma == null) idioma = Idioma.es;
    }

    public NuevoUsuarioForm(String username, String password, Rol rol, Long personaId) {
        this(username, password, rol, personaId, Idioma.es);
    }

    public static NuevoUsuarioForm vacio() {
        return new NuevoUsuarioForm("", "", Rol.USER, null);
    }
}
