package es.familia.gestordocs.compartido;

import java.io.Serializable;
import java.util.List;

/** Mensaje de aplicación sin idioma: se resuelve únicamente al presentarlo. */
public record Mensaje(String codigo, List<Object> argumentos) implements Serializable {
    public Mensaje {
        argumentos = List.copyOf(argumentos);
    }

    public static Mensaje de(String codigo, Object... argumentos) {
        return new Mensaje(codigo, List.of(argumentos));
    }
}
