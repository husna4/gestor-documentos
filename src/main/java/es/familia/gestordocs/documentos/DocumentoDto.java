package es.familia.gestordocs.documentos;

import java.time.OffsetDateTime;
import java.util.List;

import es.familia.gestordocs.personas.PersonaDto;
import es.familia.gestordocs.secciones.SeccionDto;

/**
 * Un documento con todo lo necesario para mostrarlo: ya incluye la persona, las secciones
 * y quién lo subió, para que las vistas no tengan que buscarlos.
 *
 * @param persona null si el documento no es de nadie en concreto
 */
public record DocumentoDto(
        Long id,
        String nombreVisible,
        String nombreOriginal,
        String tipoMime,
        long tamanoBytes,
        String descripcion,
        PersonaDto persona,
        List<SeccionDto> secciones,
        String subidoPor,
        OffsetDateTime subidoEn,
        boolean tieneMiniatura) {

    public boolean esImagen() {
        return tipoMime.startsWith("image/");
    }

    public boolean esPdf() {
        return "application/pdf".equals(tipoMime);
    }

    /** Si se puede ver en el visor del navegador (imágenes y PDF); el resto solo se descarga. */
    public boolean previsualizable() {
        return TiposFichero.esPrevisualizable(tipoMime);
    }

    public String icono() {
        return TiposFichero.icono(tipoMime);
    }

    /** "1,2 MB", "350 KB"... */
    public String tamanoLegible() {
        if (tamanoBytes < 1024) {
            return tamanoBytes + " B";
        }
        if (tamanoBytes < 1024 * 1024) {
            return Math.round(tamanoBytes / 1024.0) + " KB";
        }
        return String.format("%.1f MB", tamanoBytes / (1024.0 * 1024.0));
    }

    public List<Long> seccionIds() {
        return secciones.stream().map(SeccionDto::id).toList();
    }
}
