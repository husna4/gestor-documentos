package es.familia.gestordocs.usuarios.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import es.familia.gestordocs.personas.PersonaEliminadaEvent;

/** Si se borra una persona, los usuarios vinculados a ella se quedan sin persona (pero siguen existiendo). */
@Component
class PersonaEliminadaListener {

    private final UsuarioRepository usuarios;

    PersonaEliminadaListener(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    // @EventListener es síncrono: se ejecuta dentro de la transacción del borrado.
    @EventListener
    void alEliminarPersona(PersonaEliminadaEvent evento) {
        usuarios.desvincularPersona(evento.personaId());
    }
}
