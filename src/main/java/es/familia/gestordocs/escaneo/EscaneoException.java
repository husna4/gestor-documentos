package es.familia.gestordocs.escaneo;

import es.familia.gestordocs.compartido.ErrorAplicacion;
import es.familia.gestordocs.compartido.Mensaje;

public class EscaneoException extends ErrorAplicacion {
    public EscaneoException(String codigo, Object... argumentos) { super(codigo, argumentos); }

    public EscaneoException(Mensaje mensaje) { super(mensaje); }
    public EscaneoException(String codigo, Throwable causa) { super(codigo, causa); }
}
