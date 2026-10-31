package es.familia.gestordocs.usuarios;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.familia.gestordocs.compartido.NoEncontradoException;
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.personas.PersonaService;
import es.familia.gestordocs.usuarios.internal.Usuario;
import es.familia.gestordocs.usuarios.internal.UsuarioAutenticado;
import es.familia.gestordocs.usuarios.internal.UsuarioRepository;

/** API pública del módulo usuarios. Recibe y devuelve DTOs, nunca la entidad {@link Usuario}. */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

    public static final int LONGITUD_MINIMA_PASSWORD = 8;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final PersonaService personaService;

    UsuarioService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, PersonaService personaService) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.personaService = personaService;
    }

    // ---- Usuario con sesión iniciada ----

    /** Id del usuario con sesión iniciada, o vacío si no hay sesión. */
    public Optional<Long> idUsuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado autenticado) {
            return Optional.of(autenticado.getId());
        }
        return Optional.empty();
    }

    @PreAuthorize("isAuthenticated()")
    public UsuarioDto usuarioActual() {
        return usuarioActualEntidad().toDto();
    }

    /** El usuario con sesión cambia su propia contraseña, demostrando que conoce la actual. */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void cambiarPasswordPropia(String actual, String nueva) {
        var usuario = usuarioActualEntidad();
        if (!passwordEncoder.matches(actual, usuario.getPasswordHash())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        }
        validarNuevaPassword(nueva);
        if (passwordEncoder.matches(nueva, usuario.getPasswordHash())) {
            throw new ReglaNegocioException("La nueva contraseña debe ser distinta de la actual.");
        }
        usuario.cambiarPassword(passwordEncoder.encode(nueva));
    }

    // ---- Consultas ----

    /** Nombres de usuario por id, para mostrar "subido por" en los documentos. */
    public Map<Long, String> usernamesPorIds(Collection<Long> ids) {
        return usuarios.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getUsername));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioDto> listar() {
        return usuarios.findAllByOrderByUsernameAsc().stream().map(Usuario::toDto).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioDto obtener(Long id) {
        return buscarEntidad(id).toDto();
    }

    // ---- Administración ----

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UsuarioDto crear(NuevoUsuarioForm datos) {
        var username = Usuario.normalizarUsername(datos.username());
        if (usuarios.existsByUsername(username)) {
            throw new ReglaNegocioException("Ya existe el usuario «" + username + "».");
        }
        validarNuevaPassword(datos.password());
        validarPersona(datos.personaId());
        var usuario = new Usuario(username, passwordEncoder.encode(datos.password()), datos.rol(), true);
        usuario.setPersonaId(datos.personaId());
        return usuarios.save(usuario).toDto();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UsuarioDto actualizar(Long id, EdicionUsuarioForm datos) {
        var usuario = buscarEntidad(id);
        var dejaDeSerAdminActivo = usuario.esAdminActivo() && (datos.rol() != Rol.ADMIN || !datos.activo());
        if (dejaDeSerAdminActivo && esUsuarioActual(id)) {
            throw new ReglaNegocioException("No puedes quitarte el rol de administrador ni desactivarte a ti mismo.");
        }
        if (dejaDeSerAdminActivo) {
            comprobarQueQuedaOtroAdmin();
        }
        validarPersona(datos.personaId());
        usuario.setRol(datos.rol());
        usuario.setActivo(datos.activo());
        usuario.setPersonaId(datos.personaId());
        return usuario.toDto();
    }

    /** Pone una contraseña temporal; el usuario tendrá que cambiarla en su siguiente login. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void resetearPassword(Long id, String temporal) {
        if (esUsuarioActual(id)) {
            throw new ReglaNegocioException("Para cambiar tu propia contraseña usa «Cambiar contraseña».");
        }
        validarNuevaPassword(temporal);
        buscarEntidad(id).resetearPassword(passwordEncoder.encode(temporal));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void eliminar(Long id) {
        var usuario = buscarEntidad(id);
        if (esUsuarioActual(id)) {
            throw new ReglaNegocioException("No puedes borrar tu propio usuario.");
        }
        if (usuario.esAdminActivo()) {
            comprobarQueQuedaOtroAdmin();
        }
        usuarios.delete(usuario);
    }

    // ---- Auxiliares ----

    private Usuario usuarioActualEntidad() {
        return idUsuarioActual()
                .flatMap(usuarios::findById)
                .orElseThrow(() -> new IllegalStateException("No hay un usuario con sesión iniciada"));
    }

    private Usuario buscarEntidad(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new NoEncontradoException("No existe el usuario " + id));
    }

    private boolean esUsuarioActual(Long id) {
        return idUsuarioActual().map(actual -> Objects.equals(actual, id)).orElse(false);
    }

    private void comprobarQueQuedaOtroAdmin() {
        if (usuarios.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
            throw new ReglaNegocioException("Tiene que quedar al menos un administrador activo.");
        }
    }

    private void validarPersona(Long personaId) {
        if (personaId != null && !personaService.existe(personaId)) {
            throw new ReglaNegocioException("La persona elegida no existe.");
        }
    }

    private static void validarNuevaPassword(String nueva) {
        if (nueva == null || nueva.length() < LONGITUD_MINIMA_PASSWORD) {
            throw new ReglaNegocioException(
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA_PASSWORD + " caracteres.");
        }
    }
}
