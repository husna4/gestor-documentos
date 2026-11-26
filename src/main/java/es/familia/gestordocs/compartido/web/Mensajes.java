package es.familia.gestordocs.compartido.web;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import es.familia.gestordocs.compartido.ErrorAplicacion;
import es.familia.gestordocs.compartido.Mensaje;

@Component("mensajes")
public class Mensajes {
    private final MessageSource fuente;

    public Mensajes(MessageSource fuente) { this.fuente = fuente; }

    public String texto(Object valor) {
        if (valor instanceof ErrorAplicacion error) return texto(error.getMensaje());
        if (valor instanceof Mensaje mensaje) {
            var argumentos = mensaje.argumentos().stream()
                    .map(a -> a instanceof Mensaje ? texto(a) : a).toArray();
            return fuente.getMessage(mensaje.codigo(), argumentos, LocaleContextHolder.getLocale());
        }
        return valor == null ? "" : valor.toString();
    }
}
