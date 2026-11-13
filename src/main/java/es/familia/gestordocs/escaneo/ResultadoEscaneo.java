package es.familia.gestordocs.escaneo;

/** Contenido JPEG y resultado de la detección automática de bordes. */
public record ResultadoEscaneo(byte[] contenido, boolean documentoDetectado) {
}
