package es.familia.gestordocs.documentos;

import java.util.List;

/**
 * Resultado de subir varios ficheros: los que se guardaron y, para los rechazados,
 * un mensaje por fichero ("foto.exe: tipo de fichero no permitido").
 */
public record ResultadoSubida(List<DocumentoDto> subidos, List<String> errores) {
}
