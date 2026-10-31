package es.familia.gestordocs.documentos;

/** Utilidades para los nombres de fichero que vienen del usuario (que pueden traer cualquier cosa). */
final class NombresFichero {

    private static final int LONGITUD_MAXIMA = 255;

    private NombresFichero() {
    }

    /**
     * Deja solo el nombre (algunos navegadores envían la ruta completa "C:\fotos\dni.jpg"),
     * sin caracteres de control y con una longitud razonable.
     */
    static String limpiar(String nombreOriginal) {
        var nombre = nombreOriginal == null ? "" : nombreOriginal;
        nombre = nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
        nombre = nombre.replaceAll("\\p{Cntrl}", "").trim();
        if (nombre.isEmpty()) {
            nombre = "fichero";
        }
        return nombre.length() > LONGITUD_MAXIMA ? nombre.substring(nombre.length() - LONGITUD_MAXIMA) : nombre;
    }

    /** "dni-maria.jpg" → "dni-maria". */
    static String sinExtension(String nombre) {
        var punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }

    /** "jpg" de "dni-maria.jpg", o cadena vacía si no tiene. */
    static String extension(String nombre) {
        var punto = nombre.lastIndexOf('.');
        return punto > 0 && punto < nombre.length() - 1 ? nombre.substring(punto + 1) : "";
    }

    /**
     * Nombre para la descarga: el nombre visible (que el admin puede haber cambiado) con la extensión
     * del fichero original, y sin caracteres que Windows no admite en nombres de fichero.
     */
    static String paraDescarga(String nombreVisible, String nombreOriginal) {
        var base = seguroParaDisco(nombreVisible);
        var extension = extension(nombreOriginal);
        if (extension.isEmpty() || base.toLowerCase().endsWith("." + extension.toLowerCase())) {
            return base;
        }
        return base + "." + extension;
    }

    static String seguroParaDisco(String nombre) {
        var limpio = nombre.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        // Windows no admite nombres que terminen en punto o espacio.
        limpio = limpio.replaceAll("[. ]+$", "");
        return limpio.isEmpty() ? "documento" : limpio;
    }
}
