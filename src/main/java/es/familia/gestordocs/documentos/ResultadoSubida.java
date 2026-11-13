package es.familia.gestordocs.documentos;

import java.util.List;

/**
 * Resultado de subir varios ficheros: los que se guardaron y, para los rechazados,
 * un mensaje por fichero ("foto.exe: tipo de fichero no permitido").
 */
public record ResultadoSubida(List<DocumentoDto> subidos, List<String> errores, List<String> avisos) {
    public ResultadoSubida {
        subidos = List.copyOf(subidos);
        errores = List.copyOf(errores);
        avisos = List.copyOf(avisos);
    }

    public ResultadoSubida(List<DocumentoDto> subidos, List<String> errores) {
        this(subidos, errores, List.of());
    }
}
