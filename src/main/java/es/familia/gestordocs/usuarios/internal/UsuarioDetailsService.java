package es.familia.gestordocs.usuarios.internal;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Spring Security lo llama en el login (y en "Recordarme") para cargar el usuario de la BD. */
@Service
class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    UsuarioDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return usuarios.findByUsername(Usuario.normalizarUsername(username))
                .map(UsuarioAutenticado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
