package es.familia.gestordocs.secciones;

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
import es.familia.gestordocs.compartido.ReglaNegocioException;
import es.familia.gestordocs.secciones.internal.Seccion;
import es.familia.gestordocs.secciones.internal.SeccionRepository;

/** API pública del módulo secciones. Leer: cualquier usuario. Modificar: solo ADMIN. */
@Service
@Transactional(readOnly = true)
public class SeccionService {

    private final SeccionRepository secciones;
    private final ApplicationEventPublisher eventos;

    SeccionService(SeccionRepository secciones, ApplicationEventPublisher eventos) {
        this.secciones = secciones;
        this.eventos = eventos;
    }

    public List<SeccionDto> listar() {
        return secciones.findAllByOrderByNombreAsc().stream().map(Seccion::toDto).toList();
    }

    public SeccionDto obtener(Long id) {
        return buscarEntidad(id).toDto();
    }

    /** Para mostrar nombres en listados sin hacer una consulta por cada fila. */
    public Map<Long, SeccionDto> buscarPorIds(Collection<Long> ids) {
        return secciones.findAllById(ids).stream()
                .map(Seccion::toDto)
                .collect(Collectors.toMap(SeccionDto::id, Function.identity()));
    }

    public boolean existenTodas(Collection<Long> ids) {
        return ids.stream().distinct().count() == secciones.findAllById(ids).size();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SeccionDto crear(SeccionForm datos) {
        if (secciones.existsByNombreIgnoreCase(datos.nombre().trim())) {
            throw nombreRepetido();
        }
        return secciones.save(new Seccion(datos)).toDto();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SeccionDto actualizar(Long id, SeccionForm datos) {
        var seccion = buscarEntidad(id);
        if (secciones.existsByNombreIgnoreCaseAndIdNot(datos.nombre().trim(), id)) {
            throw nombreRepetido();
        }
        seccion.actualizar(datos);
        return seccion.toDto();
    }

    /**
     * Borra la sección. Sus documentos no se borran: solo pierden esta sección
     * (y si no les queda ninguna, aparecen en "Sin clasificar").
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void eliminar(Long id) {
        secciones.delete(buscarEntidad(id));
        eventos.publishEvent(new SeccionEliminadaEvent(id));
    }

    private Seccion buscarEntidad(Long id) {
        return secciones.findById(id).orElseThrow(() -> new NoEncontradoException("No existe la sección " + id));
    }

    private static ReglaNegocioException nombreRepetido() {
        return new ReglaNegocioException("Ya existe una sección con ese nombre.");
    }
}
