package es.familia.gestordocs.compartido;

/**
 * Error esperado de la lógica de negocio. Su mensaje está pensado para mostrarse
 * tal cual al usuario (por ejemplo, "La contraseña actual no es correcta").
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
