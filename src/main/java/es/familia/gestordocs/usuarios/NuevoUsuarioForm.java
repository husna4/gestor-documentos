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
        @NotBlank(message = "El usuario es obligatorio")
        @Pattern(regexp = "[A-Za-z0-9._-]{3,50}",
                message = "Entre 3 y 50 caracteres: letras sin tildes, números, punto, guion o guion bajo")
        String username,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = UsuarioService.LONGITUD_MINIMA_PASSWORD, max = 100, message = "Debe tener al menos {min} caracteres")
        String password,
        @NotNull(message = "Elige un rol") Rol rol,
        Long personaId) {

    public static NuevoUsuarioForm vacio() {
        return new NuevoUsuarioForm("", "", Rol.USER, null);
    }
}
