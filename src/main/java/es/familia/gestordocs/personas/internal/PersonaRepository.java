package es.familia.gestordocs.personas.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    List<Persona> findAllByOrderByNombreAscApellidosAsc();
}
