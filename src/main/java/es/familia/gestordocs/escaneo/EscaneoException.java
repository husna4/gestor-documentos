package es.familia.gestordocs.escaneo;

/** Fallo recuperable: su mensaje se puede mostrar sin revelar la respuesta del servidor. */
public class EscaneoException extends RuntimeException {
    public EscaneoException(String mensaje) {
        super(mensaje);
    }
}
