package es.familia.gestordocs.compartido;

/** Error funcional independiente del locale y de la capa HTTP. */
public abstract class ErrorAplicacion extends RuntimeException {
    private final Mensaje mensaje;

    protected ErrorAplicacion(String codigo, Object... argumentos) {
        this(Mensaje.de(codigo, argumentos));
    }

    protected ErrorAplicacion(Mensaje mensaje) {
        super(mensaje.codigo());
        this.mensaje = mensaje;
    }

    protected ErrorAplicacion(String codigo, Throwable causa) {
        super(codigo, causa);
        this.mensaje = Mensaje.de(codigo);
    }

    public Mensaje getMensaje() { return mensaje; }
}
