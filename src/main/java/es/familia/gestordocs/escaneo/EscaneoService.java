package es.familia.gestordocs.escaneo;

/** API del módulo: recibe una foto y devuelve su versión JPEG rectificada. */
public interface EscaneoService {
    ResultadoEscaneo escanear(byte[] imagen, String tipoMime);

    ResultadoEscaneo escanear(byte[] imagen, String tipoMime, FiltroEscaneo filtro, boolean autoRecorte);
}
