package es.familia.gestordocs.secciones;

/**
 * Se publica al borrar una sección, dentro de la misma transacción.
 * El módulo documentos lo escucha para quitar esa sección de sus documentos.
 */
public record SeccionEliminadaEvent(Long seccionId) {
}
