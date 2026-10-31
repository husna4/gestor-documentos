package es.familia.gestordocs;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import es.familia.gestordocs.usuarios.Rol;
import es.familia.gestordocs.usuarios.internal.Usuario;
import es.familia.gestordocs.usuarios.internal.UsuarioAutenticado;
import es.familia.gestordocs.usuarios.internal.UsuarioRepository;

/**
 * Crea un usuario de prueba y "inicia sesión" con él en el hilo del test, igual que
 * haría Spring Security tras un login. Como el test es transaccional, el usuario desaparece al terminar.
 */
@Component
public class SesionDePrueba {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    SesionDePrueba(UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    public UsuarioAutenticado comoAdmin() {
        return iniciar("admin-test", Rol.ADMIN);
    }

    public UsuarioAutenticado comoUsuario() {
        return iniciar("usuario-test", Rol.USER);
    }

    public UsuarioAutenticado iniciar(String username, Rol rol) {
        var usuario = usuarios.findByUsername(username)
                .orElseGet(() -> usuarios.saveAndFlush(
                        new Usuario(username, passwordEncoder.encode("password-de-prueba"), rol, false)));
        var principal = new UsuarioAutenticado(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        return principal;
    }

    public void cerrar() {
        SecurityContextHolder.clearContext();
    }
}
