package es.familia.gestordocs.documentos.internal;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Almacenamiento sustituible; las rutas y nombres nunca llegan desde el navegador. */
public interface AlmacenBorradores {
    void escribir(UUID id, String variante, byte[] contenido) throws IOException;
    byte[] leer(UUID id, String variante) throws IOException;
    long espacioUtilizado() throws IOException;
    void tocar(UUID id, Instant instante) throws IOException;
    void borrar(UUID id) throws IOException;
    void limpiarHuerfanos(Instant anterioresA, java.util.Set<UUID> activos) throws IOException;
}
