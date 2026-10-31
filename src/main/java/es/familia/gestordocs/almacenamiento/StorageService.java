package es.familia.gestordocs.almacenamiento;

import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * Dónde se guardan los ficheros. Hoy en disco ({@code DiscoStorageService}); mañana podría ser
 * MinIO o S3 implementando esta misma interfaz, sin tocar el resto de la aplicación.
 * <p>
 * Cada fichero se identifica por una clave {@code carpeta/uuid}, por ejemplo
 * {@code ficheros/0b7c...}. El nombre original del fichero nunca llega aquí.
 */
public interface StorageService {

    void guardar(String clave, InputStream contenido);

    /** @throws AlmacenamientoException si el fichero no existe o no se puede leer */
    Resource leer(String clave);

    /** Borra el fichero; no falla si ya no existía. */
    void borrar(String clave);

    boolean existe(String clave);
}
