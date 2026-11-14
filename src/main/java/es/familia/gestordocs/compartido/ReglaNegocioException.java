package es.familia.gestordocs.compartido;

public class ReglaNegocioException extends ErrorAplicacion {
    public ReglaNegocioException(String codigo, Object... argumentos) { super(codigo, argumentos); }

    public ReglaNegocioException(Mensaje mensaje) { super(mensaje); }
    public ReglaNegocioException(String codigo, Throwable causa) { super(codigo, causa); }
}
