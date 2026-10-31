package es.familia.gestordocs.usuarios;

/** Datos públicos de un usuario. Nunca incluye la contraseña. */
public record UsuarioDto(
        Long id,
        String username,
        Rol rol,
        boolean activo,
        boolean debeCambiarPassword,
        Long personaId) {
}
