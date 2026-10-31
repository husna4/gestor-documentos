package es.familia.gestordocs.almacenamiento;

/** Error de entrada/salida al guardar o leer un fichero. */
public class AlmacenamientoException extends RuntimeException {

    public AlmacenamientoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public AlmacenamientoException(String mensaje) {
        super(mensaje);
    }
}
