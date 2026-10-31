package es.familia.gestordocs.personas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos para crear o modificar una persona. Lo rellena el formulario web (y mañana, una API REST). */
public record PersonaForm(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "Máximo {max} caracteres") String nombre,
        @Size(max = 150, message = "Máximo {max} caracteres") String apellidos,
        @Size(max = 2000, message = "Máximo {max} caracteres") String notas) {

    public static PersonaForm vacio() {
        return new PersonaForm("", "", "");
    }

    public static PersonaForm desde(PersonaDto persona) {
        return new PersonaForm(persona.nombre(), persona.apellidos(), persona.notas());
    }
}
