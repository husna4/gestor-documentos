package es.familia.gestordocs.almacenamiento.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import es.familia.gestordocs.almacenamiento.AlmacenamientoException;
import es.familia.gestordocs.almacenamiento.StorageService;

/**
 * Guarda los ficheros en disco. La clave {@code ficheros/0b7c1d...} se guarda en
 * {@code <app.storage.path>/ficheros/0b/0b7c1d...}: la subcarpeta con los 2 primeros caracteres
 * evita tener miles de ficheros en un mismo directorio.
 */
@Service
@EnableConfigurationProperties(StorageProperties.class)
class DiscoStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(DiscoStorageService.class);

    /** Solo claves "carpeta/uuid": así es imposible salirse de la carpeta raíz con "../". */
    private static final Pattern CLAVE_VALIDA = Pattern.compile("[a-z]+/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path raiz;

    DiscoStorageService(StorageProperties propiedades) throws IOException {
        this.raiz = propiedades.path().toAbsolutePath().normalize();
        Files.createDirectories(raiz);
        log.info("Ficheros guardados en {}", raiz);
    }

    @Override
    public void guardar(String clave, InputStream contenido) {
        var destino = ruta(clave);
        try {
            Files.createDirectories(destino.getParent());
            // Se escribe en un temporal y luego se renombra: nunca queda un fichero a medias con el nombre bueno.
            var temporal = Files.createTempFile(destino.getParent(), "subida-", ".tmp");
            try {
                Files.copy(contenido, temporal, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporal);
            }
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo guardar el fichero " + clave, e);
        }
    }

    @Override
    public Resource leer(String clave) {
        var ruta = ruta(clave);
        if (!Files.isReadable(ruta)) {
            throw new AlmacenamientoException("No se encuentra el fichero " + clave);
        }
        return new FileSystemResource(ruta);
    }

    @Override
    public void borrar(String clave) {
        try {
            Files.deleteIfExists(ruta(clave));
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo borrar el fichero " + clave, e);
        }
    }

    @Override
    public boolean existe(String clave) {
        return Files.exists(ruta(clave));
    }

    private Path ruta(String clave) {
        if (clave == null || !CLAVE_VALIDA.matcher(clave).matches()) {
            throw new IllegalArgumentException("Clave de fichero no válida: " + clave);
        }
        var separador = clave.indexOf('/');
        var carpeta = clave.substring(0, separador);
        var nombre = clave.substring(separador + 1);
        return raiz.resolve(carpeta).resolve(nombre.substring(0, 2)).resolve(nombre);
    }
}
