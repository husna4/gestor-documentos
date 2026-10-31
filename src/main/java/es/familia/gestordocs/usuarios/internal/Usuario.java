package es.familia.gestordocs.usuarios.internal;

import java.time.OffsetDateTime;
import java.util.Locale;

import es.familia.gestordocs.usuarios.Rol;
import es.familia.gestordocs.usuarios.UsuarioDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cuenta de acceso. Entidad JPA interna del módulo: fuera de él solo circula {@link UsuarioDto}. */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword;

    @Column(name = "persona_id")
    private Long personaId;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    protected Usuario() {
        // para JPA
    }

    public Usuario(String username, String passwordHash, Rol rol, boolean debeCambiarPassword) {
        this.username = normalizarUsername(username);
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.debeCambiarPassword = debeCambiarPassword;
    }

    /** Los nombres de usuario se guardan sin espacios y en minúsculas: "Pepe " y "pepe" son el mismo. */
    public static String normalizarUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /** El propio usuario elige su contraseña: ya no tiene que cambiarla. */
    public void cambiarPassword(String nuevoHash) {
        this.passwordHash = nuevoHash;
        this.debeCambiarPassword = false;
    }

    /** El admin pone una contraseña temporal: el usuario tendrá que cambiarla al entrar. */
    public void resetearPassword(String hashTemporal) {
        this.passwordHash = hashTemporal;
        this.debeCambiarPassword = true;
    }

    public boolean esAdminActivo() {
        return activo && rol == Rol.ADMIN;
    }

    public UsuarioDto toDto() {
        return new UsuarioDto(id, username, rol, activo, debeCambiarPassword, personaId);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public boolean isDebeCambiarPassword() {
        return debeCambiarPassword;
    }

    public Long getPersonaId() {
        return personaId;
    }

    public void setPersonaId(Long personaId) {
        this.personaId = personaId;
    }
}
