package es.familia.gestordocs.usuarios;

import jakarta.validation.constraints.NotNull;

/** Lo que el admin puede cambiar de un usuario existente (el username no cambia). */
public record EdicionUsuarioForm(
        @NotNull(message = "Elige un rol") Rol rol,
        boolean activo,
        Long personaId) {

    public static EdicionUsuarioForm desde(UsuarioDto usuario) {
        return new EdicionUsuarioForm(usuario.rol(), usuario.activo(), usuario.personaId());
    }
}
