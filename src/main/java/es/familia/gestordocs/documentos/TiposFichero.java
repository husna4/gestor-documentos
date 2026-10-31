package es.familia.gestordocs.documentos;

import java.util.Map;
import java.util.Set;

/** Qué tipos de fichero se aceptan y cuáles se pueden ver en el navegador. */
public final class TiposFichero {

    /** Tipo MIME aceptado → icono de Bootstrap Icons para mostrarlo. */
    private static final Map<String, String> ACEPTADOS = Map.ofEntries(
            Map.entry("image/jpeg", "bi-file-earmark-image"),
            Map.entry("image/png", "bi-file-earmark-image"),
            Map.entry("image/gif", "bi-file-earmark-image"),
            Map.entry("image/webp", "bi-file-earmark-image"),
            Map.entry("image/heic", "bi-file-earmark-image"),
            Map.entry("image/heif", "bi-file-earmark-image"),
            Map.entry("application/pdf", "bi-file-earmark-pdf"),
            Map.entry("application/msword", "bi-file-earmark-word"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "bi-file-earmark-word"),
            Map.entry("application/vnd.oasis.opendocument.text", "bi-file-earmark-word"),
            Map.entry("application/vnd.ms-excel", "bi-file-earmark-excel"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "bi-file-earmark-excel"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", "bi-file-earmark-excel"),
            Map.entry("application/vnd.ms-powerpoint", "bi-file-earmark-ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "bi-file-earmark-ppt"),
            Map.entry("application/vnd.oasis.opendocument.presentation", "bi-file-earmark-ppt"),
            Map.entry("text/plain", "bi-file-earmark-text"));

    /** Tipos que el navegador sabe mostrar sin riesgos: se sirven "inline" en el visor. */
    private static final Set<String> PREVISUALIZABLES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");

    /** Tipos de los que Java sabe leer la imagen para sacar una miniatura. */
    private static final Set<String> CON_MINIATURA = Set.of("image/jpeg", "image/png", "image/gif");

    /** Valor del atributo {@code accept} del selector de ficheros (solo una ayuda: el servidor vuelve a validar). */
    public static final String ACCEPT_HTML =
            "image/*,.heic,.pdf,.doc,.docx,.odt,.xls,.xlsx,.ods,.ppt,.pptx,.odp,.txt";

    private TiposFichero() {
    }

    public static boolean esAceptado(String tipoMime) {
        return ACEPTADOS.containsKey(tipoMime);
    }

    public static boolean esPrevisualizable(String tipoMime) {
        return PREVISUALIZABLES.contains(tipoMime);
    }

    public static boolean admiteMiniatura(String tipoMime) {
        return CON_MINIATURA.contains(tipoMime);
    }

    public static String icono(String tipoMime) {
        return ACEPTADOS.getOrDefault(tipoMime, "bi-file-earmark");
    }
}
