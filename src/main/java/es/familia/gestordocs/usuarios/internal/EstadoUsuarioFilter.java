package es.familia.gestordocs.usuarios.internal;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * En cada petición con sesión iniciada comprueba en la BD el estado actual del usuario:
 * <ul>
 * <li>Si el admin lo ha desactivado (o borrado), cierra su sesión al momento.</li>
 * <li>Si debe cambiar la contraseña, solo le deja ir a la pantalla de cambio de contraseña.</li>
 * </ul>
 * Se consulta la BD (y no la sesión) para que los cambios del admin se apliquen sin esperar
 * a que el usuario vuelva a entrar. Con 10-20 usuarios, una consulta por petición es despreciable.
 */
class EstadoUsuarioFilter extends OncePerRequestFilter {

    static final String URL_CAMBIO_PASSWORD = "/cuenta/password";

    /** Rutas permitidas mientras el usuario está obligado a cambiar la contraseña. */
    private static final List<String> PERMITIDAS_SIN_CAMBIAR_PASSWORD =
            List.of(URL_CAMBIO_PASSWORD, "/logout", "/webjars/", "/css/", "/error");

    private final UsuarioRepository usuarios;
    private final List<LogoutHandler> cerrarSesion;

    EstadoUsuarioFilter(UsuarioRepository usuarios, TokenBasedRememberMeServices rememberMeServices) {
        this.usuarios = usuarios;
        // Invalida la sesión y además borra la cookie de "Recordarme".
        this.cerrarSesion = List.of(new SecurityContextLogoutHandler(), rememberMeServices);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado autenticado) {
            var usuario = usuarios.findById(autenticado.getId());
            if (usuario.isEmpty() || !usuario.get().isActivo()) {
                cerrarSesion.forEach(handler -> handler.logout(request, response, auth));
                response.sendRedirect(request.getContextPath() + "/login?desactivado");
                return;
            }
            if (usuario.get().isDebeCambiarPassword() && !permitidaSinCambiarPassword(request)) {
                response.sendRedirect(request.getContextPath() + URL_CAMBIO_PASSWORD);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean permitidaSinCambiarPassword(HttpServletRequest request) {
        var ruta = request.getRequestURI().substring(request.getContextPath().length());
        return PERMITIDAS_SIN_CAMBIAR_PASSWORD.stream().anyMatch(ruta::startsWith);
    }
}
