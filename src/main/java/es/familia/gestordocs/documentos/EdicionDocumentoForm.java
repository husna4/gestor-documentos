package es.familia.gestordocs.documentos;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lo que el admin puede cambiar de un documento ya subido. */
public record EdicionDocumentoForm(
        @NotBlank(message = "{validacion.nombre}")
        @Size(max = 255, message = "{validacion.maximo}") String nombreVisible,
        @Size(max = 2000, message = "{validacion.maximo}") String descripcion,
        List<Long> seccionIds,
        Long personaId) {

    public EdicionDocumentoForm {
        seccionIds = seccionIds == null ? List.of() : List.copyOf(seccionIds);
    }

    public static EdicionDocumentoForm desde(DocumentoDto documento) {
        return new EdicionDocumentoForm(documento.nombreVisible(), documento.descripcion(),
                documento.seccionIds(), documento.persona() == null ? null : documento.persona().id());
    }
}
