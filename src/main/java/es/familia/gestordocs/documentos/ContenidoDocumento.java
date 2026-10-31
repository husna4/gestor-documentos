package es.familia.gestordocs.documentos;

import org.springframework.core.io.Resource;

/**
 * El fichero de un documento, listo para enviarlo al navegador.
 *
 * @param nombreDescarga nombre con extensión que verá quien lo descargue
 */
public record ContenidoDocumento(Resource recurso, String nombreDescarga, String tipoMime, long tamano,
        boolean previsualizable) {
}
