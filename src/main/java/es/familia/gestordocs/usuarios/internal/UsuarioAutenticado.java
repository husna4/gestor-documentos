package es.familia.gestordocs.usuarios.internal;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * El usuario con sesión iniciada, tal como lo guarda Spring Security en la sesión.
 * Es el {@code User} estándar más el id de BD, para no tener que buscarlo por username.
 */
public class UsuarioAutenticado extends User {

    private final Long id;

    public UsuarioAutenticado(Usuario usuario) {
        super(usuario.getUsername(), usuario.getPasswordHash(), usuario.isActivo(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));
        this.id = usuario.getId();
    }

    public Long getId() {
        return id;
    }
}
