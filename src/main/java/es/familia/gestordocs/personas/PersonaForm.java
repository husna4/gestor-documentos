package es.familia.gestordocs.personas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos para crear o modificar una persona. Lo rellena el formulario web (y mañana, una API REST). */
public record PersonaForm(
        @NotBlank(message = "{validacion.nombre}")
        @Size(max = 100, message = "{validacion.maximo}") String nombre,
        @Size(max = 150, message = "{validacion.maximo}") String apellidos,
        @Size(max = 2000, message = "{validacion.maximo}") String notas) {

    public static PersonaForm vacio() {
        return new PersonaForm("", "", "");
    }

    public static PersonaForm desde(PersonaDto persona) {
        return new PersonaForm(persona.nombre(), persona.apellidos(), persona.notas());
    }
}
