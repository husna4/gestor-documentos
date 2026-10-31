package es.familia.gestordocs.personas;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.familia.gestordocs.compartido.NoEncontradoException;
import es.familia.gestordocs.personas.internal.Persona;
import es.familia.gestordocs.personas.internal.PersonaRepository;

/** API pública del módulo personas. Leer: cualquier usuario. Modificar: solo ADMIN. */
@Service
@Transactional(readOnly = true)
public class PersonaService {

    private final PersonaRepository personas;
    private final ApplicationEventPublisher eventos;

    PersonaService(PersonaRepository personas, ApplicationEventPublisher eventos) {
        this.personas = personas;
        this.eventos = eventos;
    }

    public List<PersonaDto> listar() {
        return personas.findAllByOrderByNombreAscApellidosAsc().stream().map(Persona::toDto).toList();
    }

    public PersonaDto obtener(Long id) {
        return buscarEntidad(id).toDto();
    }

    public boolean existe(Long id) {
        return personas.existsById(id);
    }

    /** Para mostrar nombres en listados sin hacer una consulta por cada fila. */
    public Map<Long, PersonaDto> buscarPorIds(Collection<Long> ids) {
        return personas.findAllById(ids).stream()
                .map(Persona::toDto)
                .collect(Collectors.toMap(PersonaDto::id, Function.identity()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PersonaDto crear(PersonaForm datos) {
        return personas.save(new Persona(datos)).toDto();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PersonaDto actualizar(Long id, PersonaForm datos) {
        var persona = buscarEntidad(id);
        persona.actualizar(datos);
        return persona.toDto();
    }

    /**
     * Borra la persona. Sus documentos no se borran: quedan "sin persona"
     * (lo hace el módulo documentos al recibir el evento, en esta misma transacción).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void eliminar(Long id) {
        personas.delete(buscarEntidad(id));
        eventos.publishEvent(new PersonaEliminadaEvent(id));
    }

    private Persona buscarEntidad(Long id) {
        return personas.findById(id).orElseThrow(() -> new NoEncontradoException("No existe la persona " + id));
    }
}
