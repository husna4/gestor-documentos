package es.familia.gestordocs.secciones;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos para crear o modificar una sección. */
public record SeccionForm(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "Máximo {max} caracteres") String nombre,
        @Size(max = 1000, message = "Máximo {max} caracteres") String descripcion) {

    public static SeccionForm vacio() {
        return new SeccionForm("", "");
    }

    public static SeccionForm desde(SeccionDto seccion) {
        return new SeccionForm(seccion.nombre(), seccion.descripcion());
    }
}
