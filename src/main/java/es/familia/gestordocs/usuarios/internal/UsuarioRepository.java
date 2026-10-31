package es.familia.gestordocs.usuarios.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import es.familia.gestordocs.usuarios.Rol;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Recibe el username ya normalizado (ver {@link Usuario#normalizarUsername}). */
    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    List<Usuario> findAllByOrderByUsernameAsc();

    long countByRolAndActivoTrue(Rol rol);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Usuario u set u.personaId = null where u.personaId = :personaId")
    int desvincularPersona(Long personaId);
}
