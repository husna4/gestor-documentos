package es.familia.gestordocs.documentos.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import es.familia.gestordocs.personas.PersonaEliminadaEvent;
import es.familia.gestordocs.secciones.SeccionEliminadaEvent;

/**
 * Reacciona a los borrados de otros módulos. Los {@code @EventListener} son síncronos:
 * se ejecutan dentro de la transacción del borrado, así que si algo falla no se borra nada.
 */
@Component
class BorradosListener {

    private final DocumentoRepository documentos;

    BorradosListener(DocumentoRepository documentos) {
        this.documentos = documentos;
    }

    /** Los documentos de la persona se conservan, pero pasan a ser "de nadie". */
    @EventListener
    void alEliminarPersona(PersonaEliminadaEvent evento) {
        documentos.quitarPersona(evento.personaId());
    }

    /** Los documentos se conservan; solo pierden esa sección. */
    @EventListener
    void alEliminarSeccion(SeccionEliminadaEvent evento) {
        documentos.quitarSeccion(evento.seccionId());
    }
}
