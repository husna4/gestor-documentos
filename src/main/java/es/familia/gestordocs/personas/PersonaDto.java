package es.familia.gestordocs.personas;

/** Miembro de la familia, tal como lo ven los demás módulos. */
public record PersonaDto(Long id, String nombre, String apellidos, String notas) {

    public String nombreCompleto() {
        return apellidos == null ? nombre : nombre + " " + apellidos;
    }
}
