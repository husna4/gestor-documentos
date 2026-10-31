package es.familia.gestordocs.documentos;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * Datos comunes a todos los ficheros de una subida.
 *
 * @param seccionIds secciones donde aparecerán (puede estar vacío: irán a "Sin clasificar")
 * @param personaId  persona a la que pertenecen (opcional)
 */
public record SubidaForm(
        List<Long> seccionIds,
        Long personaId,
        @Size(max = 2000, message = "Máximo {max} caracteres") String descripcion) {

    public SubidaForm {
        seccionIds = seccionIds == null ? List.of() : List.copyOf(seccionIds);
    }
}
