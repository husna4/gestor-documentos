package es.familia.gestordocs.personas;

/**
 * Se publica al borrar una persona, dentro de la misma transacción.
 * Los módulos que guardan su id (documentos, usuarios) lo escuchan para quitar la referencia.
 */
public record PersonaEliminadaEvent(Long personaId) {
}
