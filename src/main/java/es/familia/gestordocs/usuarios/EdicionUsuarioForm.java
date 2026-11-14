package es.familia.gestordocs.usuarios;

import jakarta.validation.constraints.NotNull;

/** Lo que el admin puede cambiar de un usuario existente (el username no cambia). */
public record EdicionUsuarioForm(
        @NotNull(message = "{validacion.rol}") Rol rol,
        boolean activo,
        Long personaId,
        Idioma idioma) {

    public EdicionUsuarioForm {
        if (idioma == null) idioma = Idioma.es;
    }

    public EdicionUsuarioForm(Rol rol, boolean activo, Long personaId) {
        this(rol, activo, personaId, Idioma.es);
    }

    public static EdicionUsuarioForm desde(UsuarioDto usuario) {
        return new EdicionUsuarioForm(usuario.rol(), usuario.activo(), usuario.personaId(), usuario.idioma());
    }
}
