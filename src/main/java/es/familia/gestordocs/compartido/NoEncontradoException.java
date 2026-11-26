package es.familia.gestordocs.compartido;

@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
public class NoEncontradoException extends ErrorAplicacion {
    public NoEncontradoException(String codigo, Object... argumentos) { super(codigo, argumentos); }

    public NoEncontradoException(Mensaje mensaje) { super(mensaje); }
}
